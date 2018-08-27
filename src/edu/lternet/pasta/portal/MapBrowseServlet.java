/*
 *
 * $Date$
 * $Author$
 * $Revision$
 *
 * Copyright 2011,2012 the University of New Mexico.
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

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.text.StrTokenizer;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.xpath.CachedXPathAPI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.lternet.pasta.client.DataPackageManagerClient;
import edu.lternet.pasta.client.JournalCitationsClient;
import edu.lternet.pasta.client.SEOClient;
import edu.lternet.pasta.common.EmlPackageId;
import edu.lternet.pasta.common.EmlPackageIdFormat;
import edu.lternet.pasta.common.JournalCitation;
import edu.lternet.pasta.common.ScaledNumberFormat;
import edu.lternet.pasta.common.UserErrorException;
import edu.lternet.pasta.common.eml.DataPackage;
import edu.lternet.pasta.common.eml.EmlObject;
import edu.lternet.pasta.common.eml.Entity;
import edu.lternet.pasta.common.eml.ResponsibleParty;
import edu.lternet.pasta.common.eml.Title;
import edu.lternet.pasta.portal.codegeneration.CodeGenerationServlet;
import edu.lternet.pasta.portal.user.SavedData;


/**
 * Class to compose HTML for display in the Data Package Summary page.
 * 
 * @author dcosta
 *
 */
public class MapBrowseServlet extends DataPortalServlet {

	/**
	 * Class variables
	 */

	private static final Logger logger = Logger
	    .getLogger(edu.lternet.pasta.portal.MapBrowseServlet.class);
	private static final long serialVersionUID = 1L;
	
	private static String pastaUriHead;
	private static final String forward = "./dataPackageSummary.jsp";
	private static final String PUBLISHER = "Environmental Data Initiative. ";
	private static final String DoiOrg = "https://doi.org/";
	private static final String wasDeletedMsg = 
	"This data package has been deleted by the metadata provider. It remains accessible for archival purposes only.";


	/**
	 * Constructor of the object.
	 */
	public MapBrowseServlet() {
		super();
	}
	
	
	/*
	 * Class methods 
	 */
	
	/**
	 * Composes a relative URL to the mapbrowse servlet for the specified
	 * packageId.
	 * 
	 * @param packageId  the packageId value
	 * @return the URL for the specified packageId
	 */
	public static String getRelativeURL(String packageId) {
		String mapBrowseURL = null;
		
		if (packageId != null) {
			EmlPackageIdFormat emlPackageIdFormat = new EmlPackageIdFormat();
			EmlPackageId emlPackageId = emlPackageIdFormat.parse(packageId);
			String scope = emlPackageId.getScope();
			Integer identifier = emlPackageId.getIdentifier();
			Integer revision = emlPackageId.getRevision();
			mapBrowseURL = String.format("./mapbrowse?scope=%s&identifier=%d&revision=%d", 
										scope, identifier, revision);
		}

		return mapBrowseURL;
	}

	
	/**
	 * Destruction of the servlet. <br>
	 */
	public void destroy() {
		super.destroy(); // Just puts "destroy" string in log
		// Put your code here
	}

	/**
	 * The doGet method of the servlet. <br>
	 * 
	 * This method is called when a form has its tag value method equals to get.
	 * 
	 * @param request
	 *          the request send by the client to the server
	 * @param response
	 *          the response send by the server to the client
	 * @throws ServletException
	 *           if an error occurred
	 * @throws IOException
	 *           if an error occurred
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response)
	    throws ServletException, IOException {

		doPost(request, response);

	}

	/**
	 * The doPost method of the servlet. <br>
	 * 
	 * This method is called when a form has its tag value method equals to post.
	 * 
	 * @param request
	 *          the request send by the client to the server
	 * @param response
	 *          the response send by the server to the client
	 * @throws ServletException
	 *           if an error occurred
	 * @throws IOException
	 *           if an error occurred
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		HttpSession httpSession = request.getSession();
		String titleHTML = "";
		String viewFullMetadataHTML = "";
        String moreRecentRevisionHTML = "";
		String citationHTML = "";
		String creatorsHTML = "";
		String abstractHTML = "";
		String intellectualRightsHTML = "";
		String publicationDateHTML = "";
		String spatialCoverageHTML = "";
		String googleMapHTML = "";
		String packageIdHTML = "";
		String resourcesHTML = "";
		String citationLinkHTML = "";
		String provenanceHTML = "";
        String journalCitationsHTML = "";
		String codeGenerationHTML = "";
		String digitalObjectIdentifier = "";
		String pastaDataObjectIdentifier = "";
		String savedDataHTML = "";
		String wasDeletedHTML = "";
		String seoHTML = "";
		EmlObject emlObject = null;
		boolean showSaved = false;
		boolean isSaved = false;
		boolean hasOffline = false;
		boolean wasDeleted = false;

		String uid = (String) httpSession.getAttribute("uid");

		if (uid == null || uid.isEmpty()) {
			uid = "public";
		}
		else {
			showSaved = true;
		}

		Integer id = null;
		boolean isPackageId = false;

		// Accept packageId by parts or whole
		String scope = request.getParameter("scope");
		String identifier = request.getParameter("identifier");
		String revision = request.getParameter("revision");
		String packageid = request.getParameter("packageid");

		try {
			if (scope != null && !(scope.isEmpty()) && identifier != null
					&& !(identifier.isEmpty())) {

				if (revision == null || revision.isEmpty()) {
					revision = "newest";
				}

				id = Integer.valueOf(identifier);
				isPackageId = true;
			}
			else
				if (packageid != null && !packageid.isEmpty()) {

					String[] tokens = packageid.split("\\.");

					if (tokens.length == 3) {
						scope = tokens[0];
						identifier = tokens[1];
						id = Integer.valueOf(identifier);
						revision = tokens[2];
						isPackageId = true;
					}
				}
				else {
					String msg = "A well-formed packageId was not found.";
					throw new UserErrorException(msg);
				}

			if (isPackageId) {
				StringBuilder titleHTMLBuilder = new StringBuilder();
				StringBuilder creatorsHTMLBuilder = new StringBuilder();
				StringBuilder publicationDateHTMLBuilder = new StringBuilder();
				StringBuilder spatialCoverageHTMLBuilder = new StringBuilder();
				StringBuilder googleMapHTMLBuilder = new StringBuilder();
				StringBuilder packageIdHTMLBuilder = new StringBuilder();
				StringBuilder resourcesHTMLBuilder = new StringBuilder();
				StringBuilder citationHTMLBuilder = new StringBuilder();
				StringBuilder provenanceHTMLBuilder = new StringBuilder();
				StringBuilder codeGenerationHTMLBuilder = new StringBuilder();
				StringBuilder savedDataHTMLBuilder = new StringBuilder();
                StringBuilder journalCitationsHTMLBuilder = new StringBuilder();

				String packageId = null;

				Integer size = null;
				Integer predecessor = null;
				Integer successor = null;
				String previous = "";
				String next = "";
				String revisions = "";

				String metadataUri = pastaUriHead + "metadata/eml";
				String reportUri = pastaUriHead + "report";
				String dataUri = pastaUriHead + "data/eml";

				String[] uriTokens = null;
				String entityId = null;
				String resource = null;

				String map = null;
				StrTokenizer tokens = null;
				String emlString = null;
				ArrayList<Title> titles = null;
				ArrayList<ResponsibleParty> creators = null;

				DataPackageManagerClient dpmClient = null;
                JournalCitationsClient jcClient = null;
				RevisionUtility revUtil = null;

				try {

					String robot = request.getHeader("Robot");
					dpmClient = new DataPackageManagerClient(uid, robot);
                    jcClient = new JournalCitationsClient(uid);
					
					String deletionList = dpmClient.listDeletedDataPackages();
					wasDeleted = isDeletedDataPackage(deletionList, scope, identifier);
					if (wasDeleted) {
						wasDeletedHTML = String.format("<big>%s</big>", wasDeletedMsg);
					}

					String revisionList = dpmClient.listDataPackageRevisions(
							scope, id, null);
					revUtil = new RevisionUtility(revisionList);
					size = revUtil.getSize();

					String newestRevisionValue = revUtil.getNewest().toString();
					if (revision.equals("newest"))
						revision = newestRevisionValue;
					
					if (!newestRevisionValue.equals(revision)) {
		                String displayText = "(View Newest Revision)";
		                String href = String.format("mapbrowse?scope=%s&identifier=%s&revision=%s", scope, identifier, newestRevisionValue);
		                String url = String.format("<a class=\"searchsubcat\" href=\"%s\">%s</a>", href, displayText);    
					    moreRecentRevisionHTML = String.format("&nbsp;&nbsp;%s", url);
					}

					packageId = scope + "." + id.toString() + "." + revision;
					predecessor = revUtil.getPredecessor(Integer
							.valueOf(revision));
					successor = revUtil.getSuccessor(Integer.valueOf(revision));

					emlString = dpmClient.readMetadata(scope, id, revision);
					emlObject = new EmlObject(emlString);
					titles = emlObject.getTitles();
					
					
			        try {
			            SEOClient seoClient = new SEOClient(uid);
			            seoHTML = seoClient.fetchJSON(packageId);
			        }
			        catch (Exception e) {
			            logger.error("Error fetching JSON from SEO server for packageId: " + packageId);
			            e.printStackTrace();
			        }
					
					if (showSaved) {
						SavedData savedData = new SavedData(uid);
						Integer identifierInt = new Integer(identifier);
						isSaved = savedData.hasDocid(scope, identifierInt);
					}

					if (showSaved) {
						String operation = isSaved ? "unsave" : "save";
						String display = isSaved ? "Remove from your data shelf" : "Add to your data shelf";
						String imgName = isSaved ? "minus_blue_small.png" : "plus_blue_small.png";
						
						savedDataHTMLBuilder.append("<form style=\"display:inline-block\" id=\"savedData\" class=\"form-no-margin\" name=\"savedDataForm\" method=\"post\" action=\"./savedDataServlet\" >\n");
						savedDataHTMLBuilder.append("  <input type=\"hidden\" name=\"operation\" value=\""+ operation + "\" >\n");
						savedDataHTMLBuilder.append("  <input type=\"hidden\" name=\"packageId\" value=\""+ packageId + "\" >\n");
						savedDataHTMLBuilder.append("  <input type=\"hidden\" name=\"forward\" value=\"\" >\n");
						savedDataHTMLBuilder.append("  <sup><input type=\"image\" name=\"submit\" src=\"images/" + imgName +  "\" alt=\"" + display + "\" title=\"" + display + "\"></sup>");	
						savedDataHTMLBuilder.append("</form>\n");		
						savedDataHTML = savedDataHTMLBuilder.toString();
					}

					if (titles != null) {
						titleHTMLBuilder
								.append("<ul class=\"no-list-style\">\n");

						for (Title title : titles) {
							String listItem = "<li>" + title.getTitle() + "</li>\n";
							titleHTMLBuilder.append(listItem);
						}

						titleHTMLBuilder.append("</ul>\n");
						titleHTML = titleHTMLBuilder.toString();
					}

					creators = emlObject.getCreators();

					if (creators != null) {

						creatorsHTMLBuilder
								.append("<ul class=\"no-list-style\">\n");

						for (ResponsibleParty creator : creators) {
							creatorsHTMLBuilder.append("<li>");

							boolean useFullGivenName = true;
							boolean lastNameFirst = true;
							String individualName = creator.getIndividualName(useFullGivenName, lastNameFirst);
							String positionName = creator.getPositionName();
							String organizationName = creator
									.getOrganizationName();

							if (individualName != null) {
								creatorsHTMLBuilder.append(individualName);
							}

							if (positionName != null) {
								if (individualName != null) {
									creatorsHTMLBuilder.append("; "
											+ positionName);
								}
								else {
									creatorsHTMLBuilder.append(positionName);
								}
							}

							if (organizationName != null) {
								if (positionName != null
										|| individualName != null) {
									creatorsHTMLBuilder.append("; "
											+ organizationName);
								}
								else {
									creatorsHTMLBuilder
											.append(organizationName);
								}
							}

							creatorsHTMLBuilder.append("</li>\n");
						}

						creatorsHTMLBuilder.append("</ul>\n");
						creatorsHTML = creatorsHTMLBuilder.toString();
					}
					
					String abstractText = emlObject.getAbstractText();

					if (abstractText != null) {
						abstractHTML = toSingleLine(abstractText);
					}

					
					String intellectualRightsText = emlObject.getIntellectualRightsText();

					if (intellectualRightsText != null) {
						intellectualRightsHTML = toSingleLine(intellectualRightsText);
					}

					String pubDate = emlObject.getPubDate();

					if (pubDate != null) {
						publicationDateHTMLBuilder
								.append("<ul class=\"no-list-style\">\n");
						publicationDateHTMLBuilder.append("<li>" + pubDate
								+ "</li>");
						publicationDateHTMLBuilder.append("</ul>");
						publicationDateHTML = publicationDateHTMLBuilder
								.toString();
					}

					map = dpmClient.readDataPackage(scope, id, revision);
					
					String jsonCoordinates = emlObject.jsonSerializeCoordinates();
					String stringCoordinates = emlObject.stringSerializeCoordinates();
					
					request.setAttribute("jsonCoordinates", jsonCoordinates);
					if (stringCoordinates != null && !stringCoordinates.equals("")) {

						String[] coordinatesArray = stringCoordinates.split(":");

						/*
						 * If there are two or fewer sets of coordinates, then initially
						 * show them expanded, otherwise show them collapsed (to save
						 * screen space.)
						 */
						request.setAttribute("expandCoordinates", new Boolean((coordinatesArray.length <= 2)));

						// Only use the expander widget if there's more than one set of coordinates
						boolean useExpander = (coordinatesArray.length > 1) ? true : false;
						
						if (useExpander) {
							spatialCoverageHTMLBuilder.append("<div id='jqxWidget'>\n");
							spatialCoverageHTMLBuilder.append("    <div id='jqxExpander'>\n");
							spatialCoverageHTMLBuilder.append("        <div>Geographic Coordinates</div>\n");
							spatialCoverageHTMLBuilder.append("        <div>\n");
							spatialCoverageHTMLBuilder.append("            <ul class=\"no-list-style\">\n");
							boolean firstCoordinates = true;
							
							for (String coordinates : coordinatesArray) {
								String[] nsew = coordinates.split(",");
								Double northCoord = new Double(nsew[0]);
								Double southCoord = new Double(nsew[1]);
								Double eastCoord = new Double(nsew[2]);
								Double westCoord = new Double(nsew[3]);
								if (firstCoordinates) {
									request.setAttribute("northCoord", northCoord);
									request.setAttribute("southCoord", southCoord);
									request.setAttribute("eastCoord", eastCoord);
									request.setAttribute("westCoord", westCoord);
								}
								firstCoordinates = false;
								String spatial = String.format("N: %s,  S: %s,  E: %s,  W: %s",
								             northCoord, southCoord, eastCoord, westCoord);
								spatialCoverageHTMLBuilder.append(
										String.format("  <li>%s</li>\n", spatial));	
							}
							
							spatialCoverageHTMLBuilder.append("            </ul>\n");
							spatialCoverageHTMLBuilder.append("        </div>\n");
							spatialCoverageHTMLBuilder.append("    </div>\n");
							spatialCoverageHTMLBuilder.append("</div>\n");
						}
						else {
							String[] nsew = coordinatesArray[0].split(",");						
							Double northCoord = new Double(nsew[0]);
							Double southCoord = new Double(nsew[1]);
							Double eastCoord = new Double(nsew[2]);
							Double westCoord = new Double(nsew[3]);
							request.setAttribute("northCoord", northCoord);
							request.setAttribute("southCoord", southCoord);
							request.setAttribute("eastCoord", eastCoord);
							request.setAttribute("westCoord", westCoord);
							final String spacer = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
							spatialCoverageHTMLBuilder.append("<div>\n");
							String spatial = String.format("N: %s%sS: %s%sE: %s%sW: %s",
									          northCoord, spacer, southCoord, spacer, eastCoord, spacer, westCoord);
							spatialCoverageHTMLBuilder.append(String.format("%s\n", spatial));
							spatialCoverageHTMLBuilder.append("</div>\n");
						}
						
						spatialCoverageHTML = spatialCoverageHTMLBuilder.toString();

						googleMapHTMLBuilder.append("<ul class=\"no-list-style\">\n");
						googleMapHTMLBuilder.append("  <li><div id='map-canvas-summary'></div></li>");						
						googleMapHTMLBuilder.append("</ul>\n");
						googleMapHTML = googleMapHTMLBuilder.toString();
					}

				}
				catch (Exception e) {
					logger.error(e.getMessage());
					e.printStackTrace();
					throw (e);
				}

				tokens = new StrTokenizer(map);

				URLCodec urlCodec = new URLCodec();

				boolean downloadableData = false;
				String packageIdListItem = null;
				String metadata = null;
				String report = null;
				String data = "";
				String doiId = null;
				String entityNames = dpmClient.readDataEntityNames(scope, id, revision);
				String entitySizes = dpmClient.readDataEntitySizes(scope, id, revision);
				ScaledNumberFormat scaledNumberFormat = new ScaledNumberFormat();
				
				while (tokens.hasNext()) {
					resource = tokens.nextToken();

					if (resource.contains(metadataUri)) {
						metadata = "<li><a class=\"searchsubcat\" href=\"./metadataviewer?packageid="
								+ packageId + "\">View Full Metadata</a></li>\n";	
						viewFullMetadataHTML = String.format(
	                               "<a class=\"searchsubcat\" href=\"./metadataviewer?packageid=%s\">View Full Metadata</a>",
	                               packageId);
					}
					else
						if (resource.contains(reportUri)) {

							report = "<li><a class=\"searchsubcat\" href=\"./reportviewer?packageid="
									+ packageId
									+ "\" target=\"_blank\">View Data Package Report</a></li>\n";

						}
						else if (resource.contains(dataUri)) {
								uriTokens = resource.split("/");
								entityId = uriTokens[uriTokens.length - 1];
								String entityName = null;
								String entitySize = null;
								String entitySizeStr = "";

								entityName = findEntityName(entityNames, entityId);
								entitySize = findEntitySize(entitySizes, entityId);
								
								if (entitySize != null) {
									try {
										long l = Long.parseLong(entitySize);
										String s = scaledNumberFormat.format(l);
										entitySizeStr = String.format("&nbsp;<em>(%s)</em>&nbsp;", s);
									}
									catch (NumberFormatException e) {
										entitySizeStr = String.format("&nbsp;&nbsp;<em>(%s bytes)</em>", entitySize);
									}
								}

								// Safe URL encoding of entity id
								try {
									entityId = urlCodec.encode(entityId);
								}
								catch (EncoderException e) {
									logger.error(e.getMessage());
									e.printStackTrace();
								}
								
								/*
								 * Entity name will only be returned for authorized data
								 * entities, so if it's non-null then we know the user is authorized.
								 */
								Boolean isAuthorized = false;

								if (entityName != null) {
									isAuthorized = true;
								}

								if (isAuthorized) {
									downloadableData = true;
									String objectName = emlObject.getDataPackage().findObjectName(entityName);
									String fileInfo = (objectName == null) ? entityName : objectName;
									String href = String.format("./dataviewer?packageid=%s&entityid=%s",
											                    packageId, entityId);
									String downloadLink = 
											String.format("<a class='searchsubcat' href='%s' />%s</a>",
			                                              href, fileInfo);
									data += String.format("<li><em>Name</em>: %s<br/><em>File</em>: %s %s</li>\n",
                                                          entityName, downloadLink, entitySizeStr); 
								}
								else {
									entityName = "Data object";
									String tooltip = null;
									if (uid.equals("public")) {
										tooltip = "You may need to log in before you can access this data object.";
									}
									else {
										tooltip = "You may not have permission to access this data object.";
									}
									data += String.format(
											  "<li>%s [<span name='%s' class='tooltip'><em>more info</em></span>]</li>\n", 
											  entityName, tooltip);
								}
								
								
							}
							else {

								try {
									doiId = dpmClient.readDataPackageDoi(scope,
											id, revision);
								}
								catch (Exception e) {
									logger.error(e.getMessage());
									e.printStackTrace();
								}

								pastaDataObjectIdentifier = dpmClient
										.getPastaPackageUri(scope, id, revision);

								packageIdListItem = 
										"<li>" + packageId  + "&nbsp;&nbsp;" + savedDataHTML + "</li>\n";

								if (predecessor != null) {
									previous = "<li><a class=\"searchsubcat\" href=\"./mapbrowse?scope="
											+ scope
											+ "&identifier="
											+ identifier.toString()
											+ "&revision="
											+ predecessor.toString()
											+ "\">previous revision</a></li>\n";
								}

								if (successor != null) {
									next = "<li><a class=\"searchsubcat\" href=\"./mapbrowse?scope="
											+ scope
											+ "&identifier="
											+ identifier.toString()
											+ "&revision="
											+ successor.toString()
											+ "\">next revision</a></li>\n";
								}

								if (size > 1) {
									revisions = "<li><a class=\"searchsubcat\" href=\"./revisionbrowse?scope="
											+ scope
											+ "&identifier="
											+ identifier.toString()
											+ "\">all revisions</a></li>\n";
								}

							}

				}

				packageIdHTMLBuilder.append("<ul class=\"no-list-style\">\n");
				packageIdHTMLBuilder.append(packageIdListItem);
				packageIdHTMLBuilder.append(previous);
				packageIdHTMLBuilder.append(next);
				packageIdHTMLBuilder.append(revisions);
				packageIdHTMLBuilder.append("</ul>\n");
				packageIdHTML = packageIdHTMLBuilder.toString();

				resourcesHTMLBuilder.append("<ul class=\"no-list-style\">\n");
				resourcesHTMLBuilder.append(metadata);
				resourcesHTMLBuilder.append(report);
				/*resourcesHTMLBuilder
						.append("<li>Data <sup><strong>*</strong></sup>\n");*/

				/*
				 * Check for offline entities
				 */
				ArrayList<Entity> entityList = emlObject.getDataPackage().getEntityList();
				for (Entity entity : entityList) {
					String offlineText = entity.getOfflineText();
					if (offlineText != null) {
						hasOffline = true;
						break;
					}
				}					
				if (hasOffline) {
					String offlineMsg = 
							"Offline data: the metadata describes one or more data entities that have not been made available to this repository.";
					data += String.format("<li>%s</li>\n", offlineMsg);
				}

				String listOrder = "ol";
				String downloadStr = downloadableData ? "Download Data" : "Data";
				resourcesHTMLBuilder.append(String.format("<li>%s\n", downloadStr));
				resourcesHTMLBuilder.append(String.format("<%s>\n", listOrder));
				resourcesHTMLBuilder.append(data);
				resourcesHTMLBuilder.append(String.format("</%s>\n", listOrder));
				resourcesHTMLBuilder.append("</li>\n");

				resourcesHTMLBuilder.append("<li>&nbsp;</li>\n");

				resourcesHTMLBuilder.append("<li>\n");
				resourcesHTMLBuilder.append("<div>\n");				
				resourcesHTMLBuilder.append("<form id=\"archive\" name=\"archiveform\" method=\"post\" action=\"./archiveDownload\"	target=\"_top\">\n");
				resourcesHTMLBuilder.append("  <input type=\"hidden\" name=\"packageid\" value=\"" + packageId + "\" >\n");
				resourcesHTMLBuilder.append("  <input class=\"btn btn-info btn-default\" type=\"submit\" name=\"archive\" value=\"Download Zip Archive\" >\n");
				resourcesHTMLBuilder.append("</form>\n");
				resourcesHTMLBuilder.append("</div>\n");
				resourcesHTMLBuilder.append("</li>\n");

				/*
				hasIntellectualRights = emlObject.hasIntellectualRights();
				if (hasIntellectualRights) {
				resourcesHTMLBuilder.append("<li>\n");
				resourcesHTMLBuilder
						.append("<sup><strong>*</strong></sup> <em>By downloading any data you implicitly acknowledge the "
								+ "<a class=\"searchsubcat\" href=\"./metadataviewer?packageid="
								+ packageId + "#toggleDataSetUsageRights\">Data Package Usage Rights</a> detailed in the accompanying metadata.</em>");
				resourcesHTMLBuilder.append("</li>\n");

				resourcesHTMLBuilder.append("</ul>\n");
				}
				*/
				
				resourcesHTML = resourcesHTMLBuilder.toString();


				if (doiId != null) {
					digitalObjectIdentifier = doiId;
				}

				citationHTMLBuilder
						.append("<a class=\"searchsubcat\" href=\"./dataPackageCitation?scope="
								+ scope
								+ "&"
								+ "identifier="
								+ identifier.toString()
								+ "&"
								+ "revision="
								+ revision
								+ "\">How to cite this data package</a>\n");
				citationLinkHTML = citationHTMLBuilder.toString();

				String dataSourcesXML = dpmClient.listDataSources(scope, id, revision);
				
				if ((dataSourcesXML != null) && (dataSourcesXML.length() > 0)) {
					ArrayList<DataPackage.DataSource> dataSources = xmlToDataSources(dataSourcesXML);
					if ((dataSources != null) && (dataSources.size() > 0)) {
						provenanceHTMLBuilder.append("This data package is derived from the following sources:<br/>");
						provenanceHTMLBuilder.append("<ol>\n");
						for (DataPackage.DataSource dataSource : dataSources) {
							provenanceHTMLBuilder.append(String.format("<li>%s</li>", dataSource.toHTML()));
						}
						provenanceHTMLBuilder.append("</ol>\n");
						provenanceHTMLBuilder.append("<br/>");
					}
				}
				
				String dataDescendantsXML = dpmClient.listDataDescendants(scope, id, revision);
				
				if ((dataDescendantsXML != null) && (dataDescendantsXML.length() > 0)) {
					ArrayList<DataPackage.DataDescendant> dataDescendants = xmlToDataDescendants(dataDescendantsXML);
					if ((dataDescendants != null) && (dataDescendants.size() > 0)) {
						provenanceHTMLBuilder.append("This data package is a source for the following data packages:<br/>");
						provenanceHTMLBuilder.append("<ol>\n");
						for (DataPackage.DataDescendant dataDescendant : dataDescendants) {
							provenanceHTMLBuilder.append(String.format("<li>%s</li>", dataDescendant.toHTML()));
						}
						provenanceHTMLBuilder.append("</ol>\n");
						provenanceHTMLBuilder.append("<br/>");
					}
				}
				
                String journalCitationsXML = jcClient.listDataPackageCitations(scope, id, revision);
                
                if ((journalCitationsXML != null) && (journalCitationsXML.length() > 0)) {
                    ArrayList<JournalCitation> journalCitations = JournalCitation.xmlToJournalCitations(journalCitationsXML);
                    if ((journalCitations != null) && (journalCitations.size() > 0)) {
                        journalCitationsHTMLBuilder.append("This data package has been cited in, or used as a data source for, the following journal articles:<br/>");
                        journalCitationsHTMLBuilder.append("<ol>\n");
                        for (JournalCitation journalCitation: journalCitations) {
                            journalCitationsHTMLBuilder.append(String.format("<li>%s</li>", journalCitation.toHTML()));
                        }
                        journalCitationsHTMLBuilder.append("</ol>\n");
                        journalCitationsHTMLBuilder.append("<br/>");
                    }
                }
                
                journalCitationsHTMLBuilder.append("<div>\n");             
                journalCitationsHTMLBuilder.append("<form id=\"journalcitations\" name=\"journalcitationsform\" method=\"post\" action=\"./journalCitations.jsp\" target=\"_top\">\n");
                journalCitationsHTMLBuilder.append("  <input type=\"hidden\" name=\"packageid\" id=\"packageid\" value=\"" + packageId + "\" >\n");
                journalCitationsHTMLBuilder.append("  <input class=\"btn btn-info btn-default\" type=\"submit\" name=\"journalcitationsbutton\" value=\"Add Journal Citation\" >\n");
                journalCitationsHTMLBuilder.append("</form>\n");
                journalCitationsHTMLBuilder.append("</div>\n");
                journalCitationsHTMLBuilder.append("<br/>");
                journalCitationsHTML = journalCitationsHTMLBuilder.toString();
				/*
				 * Provenance metadata generator
				 */
				provenanceHTMLBuilder.append(
						String.format(
				"Generate <a class=\"searchsubcat\" href=\"./provenanceGenerator?packageid=%s\">" +
				"provenance metadata</a> for use within your derived data package", 
				packageId));
					
				provenanceHTML = provenanceHTMLBuilder.toString();				

				/*
				 * Add code generation section only if this data package has at
				 * least one entity that is a data table.
				 */
				DataPackage dataPackage = emlObject.getDataPackage();
				boolean hasDataTableEntity = dataPackage.hasDataTableEntity();
				if (hasDataTableEntity) {
					ArrayList<String> programLinks = CodeGenerationServlet
							.getProgramLinks(packageId);
					TreeMap<String, String> programDict = CodeGenerationServlet.getProgramDict(packageId);
					codeGenerationHTMLBuilder.append("Analyze this data package using:&nbsp;");
					for (String program : programDict.keySet()) {
						codeGenerationHTMLBuilder.append(
							String.format(
                                "<input class=\"btn btn-info btn-default\" type=\"button\" onclick=\"location.href='%s';\" value=\"%s\" />",
                                programDict.get(program), program));
						codeGenerationHTMLBuilder.append("&nbsp;&nbsp;");
					}
					codeGenerationHTML = codeGenerationHTMLBuilder.toString();
					codeGenerationHTML = codeGenerationHTML.substring(0,
							codeGenerationHTML.length() - 12); // trim the last two character entities
				}

				citationHTML = this.citationFormatter(emlObject, uid, scope, id, revision);

			}

			else {
				String msg = "The 'scope', 'identifier', or 'revision' field of the packageId is empty.";
				throw new UserErrorException(msg);
			}
		}
		catch (Exception e) {
			handleDataPortalError(logger, e);
		}
		
		request.setAttribute("wasDeletedHTML", wasDeletedHTML);
		request.setAttribute("viewFullMetadataHTML", viewFullMetadataHTML);
        request.setAttribute("moreRecentRevisionHTML", moreRecentRevisionHTML);
		request.setAttribute("dataPackageTitleHTML", titleHTML);
		request.setAttribute("dataPackageCreatorsHTML", creatorsHTML);
		request.setAttribute("abstractHTML", abstractHTML);
		request.setAttribute("dataPackagePublicationDateHTML", publicationDateHTML);
		request.setAttribute("spatialCoverageHTML", spatialCoverageHTML);
		request.setAttribute("googleMapHTML", googleMapHTML);
		request.setAttribute("dataPackageIdHTML", packageIdHTML);
		request.setAttribute("dataPackageResourcesHTML", resourcesHTML);
		request.setAttribute("citationLinkHTML", citationLinkHTML);
		request.setAttribute("digitalObjectIdentifier", digitalObjectIdentifier);
		request.setAttribute("intellectualRightsHTML", intellectualRightsHTML);
		request.setAttribute("pastaDataObjectIdentifier", pastaDataObjectIdentifier);
		request.setAttribute("provenanceHTML", provenanceHTML);
		request.setAttribute("codeGenerationHTML", codeGenerationHTML);
		request.setAttribute("citationHTML", citationHTML);
        request.setAttribute("journalCitationsHTML", journalCitationsHTML);
        request.setAttribute("seoHTML", seoHTML);

		RequestDispatcher requestDispatcher = request.getRequestDispatcher(forward);
		requestDispatcher.forward(request, response);
	}
	
	
	private ArrayList<DataPackage.DataSource> xmlToDataSources(String xml) {
		ArrayList<DataPackage.DataSource> dataSources = new ArrayList<DataPackage.DataSource>();
		
  		if (xml != null) {
  			InputStream inputStream = null;
  			try {
  				inputStream = IOUtils.toInputStream(xml, "UTF-8");
  				DocumentBuilder documentBuilder = 
  	              DocumentBuilderFactory.newInstance().newDocumentBuilder();
  				CachedXPathAPI xpathapi = new CachedXPathAPI();

  				Document document = null;
  				document = documentBuilder.parse(inputStream);
  	      
  				if (document != null) { 	        
  					NodeList dataSourceNodes = xpathapi.selectNodeList(document, "//dataSource");
  					
  					for (int i = 0; i < dataSourceNodes.getLength(); i++) {
  						String packageId = null; 
  						String title = null;
  						String url = null;
  						Node dataSourceNode = dataSourceNodes.item(i);
  					    packageId = xpathapi.selectSingleNode(dataSourceNode, "packageId").getTextContent();
  					    title = xpathapi.selectSingleNode(dataSourceNode, "title").getTextContent();
  					    url = xpathapi.selectSingleNode(dataSourceNode, "url").getTextContent();
  					    url = convertToDataPortalURL(url, packageId);
  					  	DataPackage dp = new DataPackage();
  						DataPackage.DataSource dataSource = dp.new DataSource(packageId, title, url);
  						dataSources.add(dataSource);
  					}
  				}
			}
			catch (Exception e) {
		        logger.error("Error parsing search result set: " + e.getMessage());
			}
			finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					}
					catch (IOException e) {
						;
					}
				}
			}
  		}
		
		return dataSources;
	}
	
	
	/*
	 * Convert a PASTA metadata URL to a Data Portal URL. This is done as a convenience to
	 * the user, since it is easier to view a data package in the Data Portal as opposed
	 * to opening the raw XML metadata in a browser window.
	 */
	private String convertToDataPortalURL(String url, String packageId) {
		String dataPortalURL = url;
		
		if (packageId != null ) {
			EmlPackageIdFormat epif = new EmlPackageIdFormat();
			try {
				EmlPackageId epi = epif.parse(packageId);
				if (epi != null) {
					String scope = epi.getScope();
					Integer identifier = epi.getIdentifier();
					Integer revision = epi.getRevision();
					if ((scope != null) && (identifier != null) && (revision != null)) {
						dataPortalURL = String.format("mapbrowse?scope=%s&identifier=%d&revision=%d",
												      scope, identifier, revision);
					}
				}
			}
			catch (Exception e) {
				// No action needed; not a valid packageId so use the original url
			}
		}
		
		return dataPortalURL;
	}
	
	
	private ArrayList<DataPackage.DataDescendant> xmlToDataDescendants(String xml) {
		ArrayList<DataPackage.DataDescendant> dataDescendants = new ArrayList<DataPackage.DataDescendant>();
		
  		if (xml != null) {
  			InputStream inputStream = null;
  			try {
  				inputStream = IOUtils.toInputStream(xml, "UTF-8");
  				DocumentBuilder documentBuilder = 
  	              DocumentBuilderFactory.newInstance().newDocumentBuilder();
  				CachedXPathAPI xpathapi = new CachedXPathAPI();

  				Document document = null;
  				document = documentBuilder.parse(inputStream);
  	      
  				if (document != null) { 	        
  					NodeList dataDescendantNodes = xpathapi.selectNodeList(document, "//dataDescendant");
  					
  					for (int i = 0; i < dataDescendantNodes.getLength(); i++) {
  						String packageId = null; 
  						String title = null;
  						String url = null;
  						Node dataDescendantNode = dataDescendantNodes.item(i);
  					    packageId = xpathapi.selectSingleNode(dataDescendantNode, "packageId").getTextContent();
  					    title = xpathapi.selectSingleNode(dataDescendantNode, "title").getTextContent();
  					    url = xpathapi.selectSingleNode(dataDescendantNode, "url").getTextContent();
  					    url = convertToDataPortalURL(url, packageId);
  						DataPackage dp = new DataPackage();
  						DataPackage.DataDescendant dataDescendant = dp.new DataDescendant(packageId, title, url);
  						dataDescendants.add(dataDescendant);
  					}
  				}
			}
			catch (Exception e) {
		        logger.error("Error parsing search result set: " + e.getMessage());
			}
			finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					}
					catch (IOException e) {
						;
					}
				}
			}
  		}
		
		return dataDescendants;
	}
	
	
	private String findEntityName(String entityNames, String entityId) {
		String entityName = null;
		if (entityNames != null && entityId != null) {
			String[] lines = entityNames.split("\n");
			for (String line : lines) {
				if (line.startsWith(entityId)) {
					entityName = line.substring(entityId.length() + 1);
				}
			}
		}
		
		return entityName;
	}
	
	
	private String findEntitySize(String entitySizes, String entityId) {
		String entitySize = null;
		if (entitySizes != null && entityId != null) {
			String[] lines = entitySizes.split("\n");
			for (String line : lines) {
				if (line.startsWith(entityId)) {
					return line.split(",")[1];
				}
			}
		}
		
		return entitySize;
	}
	
	
	/*
	 * Compose a relative URL to the mapbrowse servlet given a metadata resource identifier
	 * as input.
	 * 		Example input:  "https://pasta.lternet.edu/package/metadata/eml/lter-landsat/7/1"
	 * 		Example output: "mapbrowse?scope=lter-landsat&identifier=7&revision=1"
	 */
	private String mapbrowseURL(String uri) {
		String url = null;
		
		if (uri != null) {
			final String patternString = "^.*/package/metadata/eml/(\\S+)/(\\d+)/(\\d+)$";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(uri);
			if (matcher.matches()) {
				String scope = matcher.group(1);
				String identifier = matcher.group(2);
				String revision = matcher.group(3);
				String displayURL = String.format("%s.%s.%s", scope, identifier, revision);
				String href = String.format("mapbrowse?scope=%s&identifier=%s&revision=%s", scope, identifier, revision);
				url = String.format("<a class=\"searchsubcat\" href=\"%s\">%s</a>", href, displayURL);
			}
		}
		
		return url;
	}
	
	
	/*
	 * Extract the package id value from a metadata resource identifier
	 * as input.
	 * 		Example input:  "https://pasta.lternet.edu/package/metadata/eml/lter-landsat/7/1"
	 * 		Example output: "lter-landsat.7.1"
	 */
	private String packageIdFromPastaId(String uri) {
		String packageId = null;
		
		if (uri != null) {
			final String patternString = "^.*/package/metadata/eml/(\\S+)/(\\d+)/(\\d+)$";
			Pattern pattern = Pattern.compile(patternString);
			Matcher matcher = pattern.matcher(uri);
			if (matcher.matches()) {
				String scope = matcher.group(1);
				String identifier = matcher.group(2);
				String revision = matcher.group(3);
				packageId = String.format("%s.%s.%s", scope, identifier, revision);
			}
		}
		
		return packageId;
	}
	
	
	/*
	 * Converts newline-separated text into a single line, so that we can display
	 * abstract text in a <textarea> HTML element without using an XLST stylesheet. 
	 * Without this conversion, the <textarea> displays the abstract in literal 
	 * layout format.
	 */
	private String toSingleLine(String text) {
		String singleLine = null;
		StringBuilder sb = new StringBuilder();
		
		String[] lines = text.split("\n");
		for (String line : lines) {
			sb.append(String.format("%s ", line.trim()));
		}
		
		singleLine = sb.toString().trim();
		return singleLine;
	}


	/**
	 * Initialization of the servlet. <br>
	 * 
	 * @throws ServletException
	 *           if an error occurs
	 */
	public void init() throws ServletException {
		PropertiesConfiguration options = ConfigurationListener.getOptions();
		pastaUriHead = options.getString("pasta.uriHead");

		if ((pastaUriHead == null) || (pastaUriHead.equals(""))) {
			throw new ServletException(
			    "No value defined for 'pasta.uriHead' property.");
		}
	}

	
	/**
	 * Formats the output for the data package citation.
	 * 
	 * @param scope
	 *          The data package scope (namespace) value
	 * @param id
	 *          The data package identifier (accession number) value
	 * @param revision
	 *          The data package revision value
	 * 
	 * @return The formatted citation as HTML
	 */
	private String citationFormatter(EmlObject emlObject, String uid, 
			                         String scope, Integer identifier, String revision) {
		String html = null;
		ArrayList<Title> titles = null;
		ArrayList<ResponsibleParty> creators = null;
		String titleText = "";
		String creatorText = "";
		String orgText = "";
		String pubDateText = null;
		String citationId = "";
		String caveat = "";
		String citationUrl = "";
		
		if (emlObject == null) {
			return html;
		}

		DataPackageManagerClient dpmClient = null;

		try {
			dpmClient = new DataPackageManagerClient(uid);
			titles = emlObject.getTitles();

			if (titles != null) {
				for (Title title : titles) {
					if (title.getTitleType().equals(Title.MAIN)) {
						titleText += title.getTitle() + ".";
					}
				}
			}

			creators = emlObject.getCreators();

			if (creators != null) {
				int personCount = emlObject.getPersonCount();
				int orgCount = emlObject.getOrgCount();
				int cnt = 0;

				// Citations should include only person names, if possible
				if (personCount != 0) {
					for (ResponsibleParty creator : creators) {	
						boolean useFullGivenName = false;;
						boolean lastNameFirst = (cnt == 0);
						String individualName = creator.getIndividualName(useFullGivenName, lastNameFirst);
						
						if (individualName != null) {
							cnt++;
							if (cnt == personCount) {
								if (cnt == 1) {
									creatorText += individualName + " ";
								}
								else {
									creatorText += individualName + ". ";
								}
							} 
							else {
								creatorText += individualName + ", ";
							}
						}
					}
				} 
				else if (orgCount != 0) { // otherwise, use organization names
					for (ResponsibleParty creator : creators) {
						String organizationName = creator.getOrganizationName();

						if (organizationName != null) {
							cnt++;
							if (cnt == orgCount) {
								creatorText += organizationName + ". ";
							} else {
								creatorText += organizationName + ", ";
							}
						}
					}
				}
			}

			try {
				citationId = dpmClient.readDataPackageDoi(scope, identifier, revision);
				citationId = citationId.replace("doi:", DoiOrg);
			} 
			catch (Exception e) {
				logger.error(e.getMessage());
				e.printStackTrace();
				citationId = dpmClient.getPastaPackageUri(scope, identifier, revision);
				caveat = "Note: DOIs are generated hourly for all data packages"
				    + " that are \"publicly\" accessible.";
			}
			
			citationUrl = "<a href=\"" + citationId + "\">" + citationId + "</a>."; 

			String pubYear = emlObject.getPubYear();

			if (pubYear != null) {
				pubDateText = pubYear + ".";
			}
			else {
				pubDateText = "";
			}

		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
			html = "<p class=\"warning\">" + e.getMessage() + "</p>\n";
			return html;
		}
		
		String datasetAccessed=datasetAccessed();

		html = String.format("<ul class=\"no-list-style\"><li>%s%s <cite>%s</cite> %s %s %s %s</li><li>%s</li></ul>", 
	               creatorText, pubDateText, titleText, orgText, PUBLISHER, citationUrl, datasetAccessed, caveat);
		
		return html;

	}
	
	
	private String datasetAccessed() {
		String datasetAccessed = null;
		
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
		String dateString = sdf.format(now);
		if (dateString.startsWith("0")) {
			dateString = dateString.substring(1);
		}
		datasetAccessed = String.format("Dataset accessed %s.", dateString);
		
		return datasetAccessed;
	}
	
	
	private boolean isDeletedDataPackage(String deletionList, String scope, String identifier) {
		boolean isDeleted = false;
		
		if (deletionList != null && !deletionList.isEmpty()) {
			String docid = String.format("%s.%s", scope, identifier);
			String[] tokens = deletionList.split("\n");

			if ((tokens != null) & (tokens.length > 0)) {
				for (String token : tokens) {
					if (docid.equals(token.trim())) {
						isDeleted = true;
						break;
					}
				}
			}
		}
		
		return isDeleted;
	}
	
}
