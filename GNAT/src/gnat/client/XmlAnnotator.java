package gnat.client;

import gnat.ISGNProperties;

/**
 * 
 * 
 * @author Joerg Hakenberg
 * 
 */

public abstract class XmlAnnotator {

	/** Name of the XML element tag that will be used to annotate genes found by GNAT. */
	public static String xml_tag = "GNAT";
	/** XML tag for a gene mention that has no ID. */
	public static String xml_tag_mention = "GNATGM";
	/** Name of the XML attribute for the {@link #xml_tag} element, which will contain the gene ID(s). */
	public static String xml_attribute_id     = "id";
	/** Name of the XML attribute for the {@link #xml_tag} element, which will contain the gene symbols(s), also known as primary terms. */
	public static String xml_attribute_symbol = "pt";
	/** Name of the XML attribute for the {@link #xml_tag} element, which will contain the species' NCBI Taxonomy ID (for example, human=9606). */
	public static String xml_attribute_species = "tax";
	/** Name of the XML attribute for the {@link #xml_tag} element, which will contain the disambiguation score, or multiple
	 *  semi-colon-separated scores in case multiple candidates are left. */
	public static String xml_attribute_score = "score";
	/** */
	public static String xml_attribute_other_ids = "otherIds";
	/** */
	public static String xml_attribute_candidate_ids = "candidateIds";

	/** Overwrite the above default XML element and attribute names according to the properties file. */
	static {
		if (ISGNProperties.get("xmlElementGeneNormalized") != null) xml_tag                     = ISGNProperties.get("xmlElementGeneNormalized");
		if (ISGNProperties.get("xmlElementGeneRecognized") != null) xml_tag_mention             = ISGNProperties.get("xmlElementGeneRecognized");
		if (ISGNProperties.get("xmlAttributeGeneId") != null)       xml_attribute_id            = ISGNProperties.get("xmlAttributeGeneId");
		if (ISGNProperties.get("xmlAttributeGeneSymbol") != null)   xml_attribute_symbol        = ISGNProperties.get("xmlAttributeGeneSymbol");
		if (ISGNProperties.get("xmlAttributeSpecies") != null)      xml_attribute_species       = ISGNProperties.get("xmlAttributeSpecies");
		if (ISGNProperties.get("xmlAttributeScore") != null)        xml_attribute_score         = ISGNProperties.get("xmlAttributeScore");
		if (ISGNProperties.get("xmlAttributeOtherIds") != null)     xml_attribute_other_ids     = ISGNProperties.get("xmlAttributeOtherIds");
		if (ISGNProperties.get("xmlAttributeCandidateIds") != null) xml_attribute_candidate_ids = ISGNProperties.get("xmlAttributeCandidateIds");
	}

	/** */
	static int verbosity = 0;

}
