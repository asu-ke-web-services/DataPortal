package edu.lternet.pasta.portal.search;


/**
 * The PageControl class manages paging of search results returned
 * by a Solr search. It generates HTML that can be displayed in the
 * Data Portal. 
 * 
 * @author dcosta
 *
 */
public class PageControl {
	
	/*
	 * Class variables
	 */
	
	private final String SPACER = "&nbsp;&nbsp;&nbsp;";
	
	
	/*
	 * Instance variables
	 */
	
	int currentPage = 1; // The current page
	int numFound;	     // The numFound value returned by Solr
	int rowsPerPage;     // The number of search results displayed per page
	int start;           // The start record
	int pageRange = 5;   // View +/- pageRange pages from the current page
	boolean isSavedDataPage; // true if displaying saved data packages, else false
	String sort = null;
	
	String titleSort = null;
	String creatorsSort = null;
	String pubDateSort = null;
	String packageIdSort = null;
	String relevanceSort = null;
	

	/*
	 * Constructor
	 */
	
	public PageControl(int numFound, int start, int rowsPerPage, String sort, boolean isSavedDataPage) {
		this.numFound = numFound;
		this.rowsPerPage = rowsPerPage;
		this.sort = sort;
		initSortValues(sort);
		this.isSavedDataPage = isSavedDataPage;
		setStart(start);
	}
	
	
	/*
	 * Class methods
	 */
	
	/**
	 * A main method to test page control logic. Prints out the HTML string
	 * that is generated by the PageControl object.
	 * 
	 * @param args[0]   numFound, the number of matches found
	 * @param args[1]   rowsPerPage, the number of rows to display on one page
	 * @param args[2]   start, the start row
	 */
	public static void main(String[] args) {
		int numberFound = new Integer(args[0]);
		int rowsPerPage = new Integer(args[1]);
		int start = new Integer(args[2]);
		boolean isSavedDataPage = false;
		String sort = Search.DEFAULT_SORT;
		
		PageControl pageControl = new PageControl(numberFound, start, rowsPerPage, sort, isSavedDataPage);
		String html = pageControl.composePageBody();
		System.out.println(html);
	}
	
	
	/*
	 * Intance methods
	 */
	
	
	/*
	public String composeDropDown() {
		String html = "";
		String titleSort = getTitleSort();
		String creatorsSort = getCreatorsSort();
		String pubDateSort = getPubDateSort();
		String packageIdSort = getPackageIdSort();
		String relevanceSort = getRelevanceSort();
		
		StringBuilder sb = new StringBuilder("<select class=\"select-width-auto\" id=\"sortBy\" name=\"sortBy\" onchange=\"processSortBy(this)\">\n");
		sb.append(String.format("  <option value=\"%s\">%s</option>\n", "relevance", "relevance"));
		sb.append(String.format("  <option value=\"%s\">%s</option>\n", "creators", "creators"));
		sb.append(String.format("  <option value=\"%s\">%s</option>\n", "packageId", "package identifier"));
		sb.append(String.format("  <option value=\"%s\">%s</option>\n", "pubDate", "publication date"));
		sb.append(String.format("  <option value=\"%s\">%s</option>\n", "title", "title"));
		sb.append("</select>\n");
		
		html = sb.toString();
		
		return html;
	} */
	
	
	private String composeStartTag(int linkStart) {
		String servletName = this.isSavedDataPage ? "savedDataServlet" : "simpleSearch";
		String href = String.format("%s?start=%d&rows=%d&sort=%s", servletName, linkStart, rowsPerPage, sort);
		String aStartTag = String.format("<a class='searchsubcat' href='%s'>", href);
		return aStartTag;		
	}
	
	
	/**
	 * Generates HTML for the header paragraph of the search results page
	 * 
	 * @return   a string of HTML
	 */
	public String composePageHeader() {
		String html = "";

		if (numFound > 0) {
			String plural = numFound != 1 ? "s" : "";
			//String verb = numFound != 1 ? "are" : "is";
			int lo = ((currentPage - 1) * rowsPerPage) + 1;
			int hi = lo + rowsPerPage - 1;
			if (hi > numFound)
				hi = numFound;
			StringBuilder sb = new StringBuilder("<p>");
			if (this.isSavedDataPage) {
				sb.append(String.format(
						"<span><big><span id='dataShelfNumFound'>%d</span> data package(s) on your data shelf</big></span>", numFound));
			}
			else {
				sb.append(String.format(
					"Displaying %d-%d of %d matching data package%s", lo, hi,
					numFound, plural));
			}
			sb.append("</p>");

		html = sb.toString();
		}
		
		return html;
	}
	
	
	/**
	 * Generates HTML for the body paragraph of the search results page
	 * 
	 * @return  a string of HTML
	 */
	public String composePageBody() {
		String html = "";
		int max = highestPage();
		
		if (max > 1) {
		StringBuilder sb = new StringBuilder("<p align='center'>");
		
		if (currentPage > 1) {
			int linkStart = 0;
			String aStartTag = composeStartTag(linkStart);
			sb.append(aStartTag);
		}
		sb.append("&lt;&lt;");
		if (currentPage > 1) {
			sb.append("</a>");
		}
		sb.append(SPACER);
		
		if (currentPage > 1) {
			int linkStart = (currentPage - 2) * rowsPerPage;
			String aStartTag = composeStartTag(linkStart);
			sb.append(aStartTag);
		}
		sb.append("&lt;");
		if (currentPage > 1) {
			sb.append("</a>");
		}
		sb.append(SPACER);
		
		// Display the page numbers
		for (int i = 1; i <= max; i++) {
			if (isWithinPageRange(i)) {
				String aStartTag = null;
				String aEndTag = null;
				
				// All but the current page are links
				if (i == currentPage) {
					aStartTag = "";
					aEndTag = "";
				}
				else {
					int linkStart = (i - 1) * rowsPerPage;
					aStartTag = composeStartTag(linkStart);
					aEndTag = "</a>";
				}
				
				sb.append(String.format("%s%d%s%s", 
										aStartTag, i, aEndTag, SPACER));
			}
		}
		
		if (currentPage < max) {
			int linkStart = currentPage * rowsPerPage;
			String aStartTag = composeStartTag(linkStart);
			sb.append(aStartTag);
		}
		sb.append("&gt;");
		if (currentPage < max) {
			sb.append("</a>");
		}
		sb.append(SPACER);
		
		if (currentPage < max) {
			int linkStart = (max - 1) * rowsPerPage;
			String aStartTag = composeStartTag(linkStart);
			sb.append(aStartTag);
		}
		sb.append("&gt;&gt;");
		if (currentPage < max) {
			sb.append("</a>");
		}
		sb.append(SPACER);
		
		sb.append("</p>");
		html = sb.toString();
		}
		
		return html;
	}

	
	/* 
	 * Getter methods 
	 */
	
	public int getNumFound() {
		return numFound;
	}

	
	public int getRecordsPerPage() {
		return rowsPerPage;
	}

	
	public int getCurrentPage() {
		return currentPage;
	}
	
	
	public int getStartRow() {
		int startRow = 0;
		
		startRow = (currentPage - 1) * rowsPerPage;
		
		return startRow;
	}
	
	
	public String getTitleSort() {
		return titleSort;
	}
	
	
	public String getCreatorsSort() {
		return creatorsSort;
	}
	
	
	public String getPubDateSort() {
		return pubDateSort;
	}
	
	
	public String getPackageIdSort() {
		return packageIdSort;
	}
	
	
	public String getRelevanceSort() {
		return relevanceSort;
	}
	
	
	/**
	 * Calculates the highest page value based on the numFound
	 * and rowsPerPage values.
	 * 
	 * @return  the highestPage value
	 */
	public int highestPage() {
		int highestPage = 0;
		
		highestPage = numFound / rowsPerPage;
		if (numFound % rowsPerPage > 0) highestPage++;
		
		return highestPage;
	}
	
	
	private void initSortValues(String sort) {
		titleSort = String.format("%s,%s", Search.TITLE_SORT, Search.SORT_ORDER_ASC);
		creatorsSort = String.format("%s,%s", Search.CREATORS_SORT, Search.SORT_ORDER_ASC);
		pubDateSort = String.format("%s,%s", Search.PUBDATE_SORT, Search.SORT_ORDER_ASC);
		packageIdSort = String.format("%s,%s", Search.PACKAGEID_SORT, Search.SORT_ORDER_ASC);
		relevanceSort = Search.DEFAULT_SORT;
		
		if (sort.equals(titleSort)) {
			titleSort = String.format("%s,%s", Search.TITLE_SORT, Search.SORT_ORDER_DESC);
		}
		else if (sort.equals(creatorsSort)) {
			creatorsSort = String.format("%s,%s", Search.CREATORS_SORT, Search.SORT_ORDER_DESC);
		}
		else if (sort.equals(pubDateSort)) {
			pubDateSort = String.format("%s,%s", Search.PUBDATE_SORT, Search.SORT_ORDER_DESC);
		}
		else if (sort.equals(packageIdSort)) {
			packageIdSort = String.format("%s,%s", Search.PACKAGEID_SORT, Search.SORT_ORDER_DESC);
		}
	}
	
	
	/*
	 * Does the current page fall within the display range?
	 */
	private boolean isWithinPageRange(int pageNumber) {
		boolean withinRange = false;
		int pageFulcrum = currentPage;  // center page display around the page fulcrum
		int max = highestPage();
		
		if (currentPage < (pageRange + 1)) {
			pageFulcrum = (pageRange + 1);
		}
		else if ((currentPage + pageRange) > max) {
			pageFulcrum = max - pageRange;
		}
		
		
		if (Math.abs(pageNumber - pageFulcrum) <= pageRange) {
			withinRange = true;
		}
		
		return withinRange;
	}

	
	/*
	 * Setter methods
	 */
	
	public void setNumFound(int recordsFound) {
		this.numFound = recordsFound;
	}

	
	public void setRowsPerPage(int rowsPerPage) {
		this.rowsPerPage = rowsPerPage;
	}

	
	public int setCurrentPage(int newPage) {
		int returnValue = -1;
		
		if ((newPage > 0) && (newPage <= highestPage())) {
			this.currentPage = newPage;
			returnValue = newPage;
		}
		
		return returnValue;
	}
	
	
	public void setStart(int start) {
		this.start = start;
		int newPage = (start / rowsPerPage) + 1;
		setCurrentPage(newPage);
	}

}