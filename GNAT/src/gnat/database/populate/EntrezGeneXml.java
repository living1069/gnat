package gnat.database.populate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Entrez Gene XML to generate database tables.
 * 
 * @author J&ouml;rg Hakenberg &lt;joerg.hakenberg@gmail.com&gt;
 */
public class EntrezGeneXml {

	/**
	 * Maps a table name ("GR_AllNames") to the XPath query that will extract the corresponding data from Entrez Gene XML.
	 */
	static Map<String, String> xqueries = new LinkedHashMap<String, String>();
	static {
		xqueries.put("GR_AllNames",  "");
		xqueries.put("GR_ChrLocation",  "");
		xqueries.put("GR_GeneRef",  "");
		xqueries.put("GR_GeneRIF",  "");
		xqueries.put("GR_GOID",  "");
		xqueries.put("GR_GOTerm",  "");
		xqueries.put("GR_Interactorname",  "");
		xqueries.put("GR_Names",  "");
		xqueries.put("GR_Origin",  "");
		xqueries.put("GR_ProteinDisease",  "");
		xqueries.put("GR_ProteinDomain",  "");
		xqueries.put("GR_ProteinFunction",  "");
		xqueries.put("GR_ProteinInteraction",  "");
		xqueries.put("GR_ProteinKeywords",  "");
		xqueries.put("GR_Symbols",  "");
		//xqueries.put("GR_",  "");
	}
	
	/**
	 * 
	 * @param args
	 */
	public static void main (String[] args) {
		
		
		
	}
	
}
