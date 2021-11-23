/* 
 * Original work: Copyright (c) 2010-2011 Brigham Young University
 * Modified work: Copyright (c) 2017 Xilinx, Inc. 
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
package com.xilinx.rapidwright.gui;

import java.util.Comparator;
import java.util.HashMap;

import io.qt.core.Qt.ItemDataRole;
import io.qt.widgets.QTreeWidget;
import io.qt.widgets.QTreeWidgetItem;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.Part;
import com.xilinx.rapidwright.device.PartNameTools;

public class WidgetMaker {
	
	
	public static QTreeWidget createAvailablePartTreeWidget(String header){
		QTreeWidget treeWidget = new QTreeWidget();
		treeWidget.setColumnCount(1);
		treeWidget.setHeaderLabel(header);
		
		HashMap<String, QTreeWidgetItem> familyItems = new HashMap<String, QTreeWidgetItem>();

		Device.getAvailableDevices().stream()
				.map(PartNameTools::getPart)
				.sorted(Comparator.comparing((Part p) -> PartNameTools.getFullArchitectureName(p.getArchitecture())).thenComparing(Part::getName))
				.forEach(p -> {
					//FamilyType type = p.getArchitecture();
					String type = PartNameTools.getFullArchitectureName(p.getArchitecture());
					QTreeWidgetItem familyItem = familyItems.get(type);
					if(familyItem == null){
						familyItem = new QTreeWidgetItem(treeWidget);
						familyItem.setText(0, type);
						familyItems.put(type, familyItem);
					}
					QTreeWidgetItem partItem = null;
					QTreeWidgetItem parent = familyItem;

					partItem = new QTreeWidgetItem(parent);
					partItem.setText(0, p.getDevice());
					partItem.setData(0, ItemDataRole.AccessibleDescriptionRole, p.getDevice());
				});

		return treeWidget;
	}
}
