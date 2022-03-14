/* 
 * Copyright (c) 2022 Xilinx, Inc. 
 * All rights reserved.
 *
 * Author: Jakob Wenzel, Xilinx Research Labs.
 *  
 * This file is part of RapidWright. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
 
package com.xilinx.rapidwright.edif;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.util.ParallelismTools;
import com.xilinx.rapidwright.util.function.InputStreamSupplier;

public class ParallelEDIFParser implements AutoCloseable{
    private static final long SIZE_PER_THREAD = EDIFTokenizer.DEFAULT_MAX_TOKEN_LENGTH * 10L;
    protected final List<ParallelEDIFParserWorker> workers = new ArrayList<>();
    protected final Path fileName;
    private final long fileSize;
    protected final InputStreamSupplier inputStreamSupplier;
    protected final int maxTokenLength;
    protected NameUniquifier uniquifier = NameUniquifier.concurrentUniquifier();

    ParallelEDIFParser(Path fileName, long fileSize, InputStreamSupplier inputStreamSupplier, int maxTokenLength) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.inputStreamSupplier = inputStreamSupplier;
        this.maxTokenLength = maxTokenLength;
    }

    public ParallelEDIFParser(Path fileName, long fileSize, InputStreamSupplier inputStreamSupplier) {
        this(fileName, fileSize, inputStreamSupplier, EDIFTokenizer.DEFAULT_MAX_TOKEN_LENGTH);
    }

    public ParallelEDIFParser(Path p, long fileSize) {
        this(p, fileSize, InputStreamSupplier.fromPath(p));
    }

    public ParallelEDIFParser(Path p) throws IOException {
        this(p, Files.size(p));
    }

    protected ParallelEDIFParserWorker makeWorker(long offset) throws IOException {
        return new ParallelEDIFParserWorker(fileName, inputStreamSupplier.get(), offset, uniquifier, maxTokenLength);
    }

    private int calcThreads(long fileSize) {
        int maxUsefulThreads = Math.max((int) (fileSize / SIZE_PER_THREAD),1);
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.min(maxUsefulThreads, processors);
    }


    protected void initializeWorkers() throws IOException {
        workers.clear();
        int threads = calcThreads(fileSize);
        long offsetPerThread = fileSize / threads;
        for (int i=0;i<threads;i++) {
            ParallelEDIFParserWorker worker = makeWorker(i*offsetPerThread);
            workers.add(worker);
        }
    }

    private int numberOfThreads;
    public EDIFNetlist parseEDIFNetlist() throws IOException {

        initializeWorkers();
        numberOfThreads = workers.size();

        final List<ParallelEDIFParserWorker> failedWorkers = ParallelismTools.maybeToParallel(workers.stream())
                .filter(w -> !w.parseFirstToken())
                .collect(Collectors.toList());
        if (!failedWorkers.isEmpty() && !Device.QUIET_MESSAGE) {
            for (ParallelEDIFParserWorker failedWorker : failedWorkers) {
                if (failedWorker.parseException!=null) {
                    String message = failedWorker.parseException.getMessage();
                    //Full message contains a hint to a constant that the user should adjust.
                    //Token misdetection is the more likely cause, so let's cut off that part
                    final String overflowPrefix = "ERROR: String buffer overflow";
                    if (message.startsWith(overflowPrefix)) {
                        message = overflowPrefix;
                    }
                    System.err.println("Removing failed thread "+failedWorker+": "+ message);
                } else {
                    System.err.println("Removing "+failedWorker+", it started past the last cell.");
                }
            }
        }
        for (ParallelEDIFParserWorker failedWorker : failedWorkers) {
            failedWorker.close();
        }
        workers.removeAll(failedWorkers);

        //Propagate parse limit to neighbours
        for (int i = 1; i < workers.size(); i++) {
            workers.get(i - 1).setStopCellToken(workers.get(i).getFirstCellToken());
        }

        doParse();

        final EDIFNetlist netlist = mergeParseResults();

        for (ParallelEDIFParserWorker worker : workers) {
            worker.printParseStats(fileSize);
        }
        return netlist;
    }


    private void doParse() {
        ParallelismTools.maybeToParallel(workers.stream()).forEach(ParallelEDIFParserWorker::doParse);

        //Check if we had misdetected start tokens
        for (int i=0; i<workers.size();i++) {
            final ParallelEDIFParserWorker worker = workers.get(i);
            if (worker.parseException!=null) {
                throw worker.parseException;
            }
            while (worker.stopTokenMismatch) {
                if (i<workers.size()-1) {
                    final ParallelEDIFParserWorker failedWorker = workers.get(i + 1);
                    if (!Device.QUIET_MESSAGE) {
                        System.err.println("Token mismatch between "+worker+" and " + failedWorker + ". Discarding second one and reparsing...");
                    }
                    try {
                        failedWorker.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    workers.remove(i+1);
                } else {
                    throw new IllegalStateException(worker+" claims to have a mismatch with the following thread, but is is the last one");
                }

                if (i<workers.size()-1) {
                    worker.setStopCellToken(workers.get(i + 1).getFirstCellToken());
                } else {
                    worker.setStopCellToken(null);
                }

                //Re-Parse :(
                worker.doParse();
                if (worker.parseException!=null) {
                    throw worker.parseException;
                }
            }
        }
    }

    private EDIFDesign getEdifDesign() {
        //We can't just ask the last thread, since it may have parsed nothing at all. The designInfo is then in
        //the previous thread.
        for (int i=workers.size()-1;i>=0;i--) {
            final ParallelEDIFParserWorker worker = workers.get(i);
            if (worker.edifDesign != null) {
                return worker.edifDesign;
            }
        }
        return null;
    }

    private void addCellsAndLibraries(EDIFNetlist netlist) {
        EDIFLibrary currentLibrary = null;
        EDIFToken currentToken = null;
        for (ParallelEDIFParserWorker worker : workers) {
            for (ParallelEDIFParserWorker.LibraryOrCellResult parsed : worker.librariesAndCells) {
                if (currentToken!=null && parsed.getToken().byteOffset<= currentToken.byteOffset) {
                    throw new IllegalStateException("Not in ascending order! seen: "+currentToken+", now processed "+parsed.getToken());
                }
                currentToken = parsed.getToken();

                currentLibrary = parsed.addToNetlist(netlist, currentLibrary);
            }
        }
    }

    private void processCellInstLinks(EDIFNetlist netlist) {
        final Map<String, EDIFLibrary> librariesByLegalName = netlist.getLibraries().stream()
                .collect(Collectors.toMap(EDIFName::getLegalEDIFName, Function.identity()));

        ParallelismTools.maybeToParallel(workers.stream()).forEach(w->w.linkCellReferences(librariesByLegalName));
    }

    private void processPortInstLinks() {
        ParallelismTools.maybeToParallel(workers.stream()).forEach(ParallelEDIFParserWorker::linkPortInsts);
    }

    private EDIFNetlist mergeParseResults() {
        EDIFNetlist netlist = Objects.requireNonNull(workers.get(0).netlist);
        netlist.setDesign(getEdifDesign());

        addCellsAndLibraries(netlist);
        processCellInstLinks(netlist);
        processPortInstLinks();

        return netlist;
    }

    @Override
    public void close() throws IOException {
        for (ParallelEDIFParserWorker worker : workers) {
            worker.close();
        }
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public int getSuccesfulThreads() {
        return workers.size();
    }
}
