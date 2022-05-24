/* 
 * Copyright (c) 2021 Xilinx, Inc. 
 * All rights reserved.
 *
 * Author: Chris Lavin, Xilinx Research Labs.
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
 
package com.xilinx.rapidwright.design;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.support.RapidWrightDCP;
import com.xilinx.rapidwright.tests.CodePerfTracker;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.Installer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the EDIF auto-generate mechanism when reading DCPs
 * 
 */
public class TestDCPLoad {

    @Test
    public void checkAutoEDIFGenerationFailure() {
        Path dcpPath = RapidWrightDCP.getPath("picoblaze_ooc_X10Y235_unreadable_edif.dcp");

        Design.setAutoGenerateReadableEdif(false);
        Assertions.assertThrows(RuntimeException.class, () -> {
            Design.readCheckpoint(dcpPath, CodePerfTracker.SILENT);
        });
    }

    @Test
    public void checkAutoEDIFGenerationSuccess() {
        Design design = RapidWrightDCP.loadDCP("picoblaze_ooc_X10Y235.dcp");
        Path dcpPath = RapidWrightDCP.getPath("picoblaze_ooc_X10Y235_unreadable_edif.dcp");

        // Simulate auto-generation using Vivado by placing a readable EDIF into
        // the output directory
        // the output directory
        Path readableEDIFDir = DesignTools.getDefaultReadableEDIFDir(dcpPath);
        Path readableEDIF = DesignTools.getEDFAutoGenFilePath(dcpPath, readableEDIFDir);
        FileTools.makeDirs(readableEDIFDir.toString());
        EDIFTools.writeEDIFFile(readableEDIF, design.getNetlist(), design.getPartName());
        FileTools.writeStringToTextFile(Installer.calculateMD5OfFile(dcpPath),
                DesignTools.getDCPAutoGenMD5FilePath(dcpPath, readableEDIFDir).toString());
        
        Design.setAutoGenerateReadableEdif(true);
        Design.readCheckpoint(dcpPath, CodePerfTracker.SILENT);
    }
    
    @Test
    public void checkAutoEDIFGenerationWithVivado() throws IOException {
        // This test won't run in GH as Vivado is not available
        Assumptions.assumeTrue(FileTools.isVivadoOnPath());
        
        Path dcpPath = RapidWrightDCP.getPath("picoblaze_ooc_X10Y235_unreadable_edif.dcp");

        Path readableEDIFDir = DesignTools.getDefaultReadableEDIFDir(dcpPath);
        Path readableEDIF = DesignTools.getEDFAutoGenFilePath(dcpPath, readableEDIFDir);
        FileTools.deleteFile(readableEDIF.toString());

        Design.setAutoGenerateReadableEdif(true);
        Design.readCheckpoint(dcpPath, CodePerfTracker.SILENT);
        Assertions.assertTrue(Files.getLastModifiedTime(readableEDIF).toMillis() >
                Files.getLastModifiedTime(dcpPath).toMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"picoblaze_ooc_X10Y235_2022_1.dcp"})
    public void testDCPFromVivado2022_1(String dcp) {
        RapidWrightDCP.loadDCP(dcp);
    }
}
