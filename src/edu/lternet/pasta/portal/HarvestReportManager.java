/*
 *
 * $Date: 2015-02-11 11:10:19 -0700 (Wed, 11 Feb 2015) $
 * $Author: dcosta $
 * $Revision: $
 *
 * Copyright 2011-2015 the University of New Mexico.
 *
 * This work was supported by National Science Foundation Cooperative
 * Agreements #DEB-0832652 and #DEB-0936498.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package edu.lternet.pasta.portal;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.log4j.Logger;


public class HarvestReportManager implements Runnable {

	/*
	 * Class variables
	 */
	private static final Logger logger = Logger
			.getLogger(HarvestReportManager.class);
	
	/*
	 * Instance variables
	 */
	private long ttl;
	private String harvestReportPath = null;
	
	
	/*
	 * Constructors
	 */
	
	
	/**
	 * Constructs a HarvestReportManager object
	 * @param dataPath  the path to the top-level harvest reports directory
	 * @param ttl   the time-to-live in milliseconds for harvest reports
	 */
	public HarvestReportManager(String dataPath, long ttl) {
		this.harvestReportPath = dataPath;
		this.ttl = ttl;
	}
	
	
	/*
	 * The run method executes in a separate thread
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		File file = new File(harvestReportPath);

		File[] directories = file.listFiles(new FilenameFilter() {
		  @Override
		  public boolean accept(File current, String name) {
		    return new File(current, name).isDirectory();
		  }
		});
		
		for (File directory : directories) {
			String dataPath = directory.getPath();
			cleanExpiredReports(dataPath, ttl);
		}
	}
	

	/**
	 * Removes any harvest report that is older than the specified time-to-live
	 * (ttl).
	 * 
	 * @param ttl
	 *            The time-to-live value in milliseconds.
	 */
	private void cleanExpiredReports(String dataPath, Long ttl) {
		int depthLimit = 3;
		Date now = new Date();
		Date expirationDate = new Date(now.getTime() - ttl);
		boolean acceptOlder = true;
		File harvestReportDir = new File(dataPath);
		logger.info(String.format("Cleaning expired harvest report directories under: %s", dataPath));
		FileFilter ageFileFilter = FileFilterUtils.ageFileFilter(expirationDate, acceptOlder);
		HarvestReportCleaner harvestReportCleaner = new HarvestReportCleaner(ageFileFilter, depthLimit);

		try {
			List<File> cleanedFiles = harvestReportCleaner.clean(harvestReportDir);
			int dirCount = 0;
			for (File file : cleanedFiles) {
				String pathname = file.getPath();
				logger.info(String.format("  Cleaned directory: %s", pathname));
				dirCount++;
			}
			String nounVerb = (dirCount == 1) ? "directory was" : "directories were";
		    logger.info(String.format("%d %s cleaned on this pass.", dirCount, nounVerb));
		}
		catch (IOException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}
	
}
