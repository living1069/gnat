package gnat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import gnat.ConstantsNei;
import gnat.ISGNProperties;
import gnat.filter.nei.GeneRepositoryLoader;
import gnat.filter.nei.IdentifyAllFilter;
import gnat.filter.nei.ImmediateContextFilter;
import gnat.filter.nei.MultiSpeciesDisambiguationFilter;
import gnat.filter.nei.NameValidationFilter;
import gnat.filter.nei.RecognizedEntityUnifier;
import gnat.filter.nei.SpeciesFrequencyFilter;
import gnat.filter.nei.StopWordFilter;
import gnat.filter.nei.UnambiguousMatchFilter;
import gnat.filter.nei.UnspecificNameFilter;
import gnat.filter.ner.DefaultSpeciesRecognitionFilter;
import gnat.filter.ner.RunAllGeneDictionaries;
import gnat.preprocessing.NameRangeExpander;
import gnat.representation.Gene;
import gnat.representation.IdentificationStatus;
import gnat.representation.RecognizedEntity;
import gnat.representation.Text;
import gnat.representation.TextAnnotation;
import gnat.representation.TextFactory;
import gnat.utils.Sorting;
import gnat.utils.StringHelper;

public class AnnotatePubMedXml extends XmlAnnotator {

	public static void main (String[] args) {
		// check for command line parameters
		if (args.length == 0 || args[0].matches("\\-\\-?h(elp)?")) {
			System.out.println("AnnotatePubMedXml -- annotates genes in individual PubMed XML files\n" +
					           "Supported file formats:\n" +
					           "- Medline XML (MedlineCitation),\n" +
					           "- PubMed XML (PubmedArticle),\n" +
					           "- GZipped Medline/Pubmed XML files like '12345678.xml.gz'");
			System.out.println("Call: AnnotatePubMedXml <dir>");
			System.out.println(" <dir>            -  directory with one or more .xml or .xml.gz files");
			System.out.println("Optional parameters:");
			System.out.println(" -g               -  Print only those texts to the output that have a gene");
			System.out.println(" -v               -  Set verbosity level for progress and debugging information");
			System.out.println("                     Default: 0; warnings: 1, status: 2, ... debug: 6");
			System.out.println(" --outdir <dir>   -  Folder in which to write the output XML");
			System.out.println("                     By default, will write into the current directory.");
			System.out.println(" --list <list>    -  Annotate files found in <list>");
			System.out.println(" --ignore <file>  -  Ignore the files listed in <file>");
			//System.out.println(" --all            -  annotate all XML files in the folder, even those not matching the pattern 'medline<year>n<number>.xml(.gz)'");
			System.exit(1);
		}

		// parse and store command line parameters
		String dir = "";        // directory to read from
		String outDir = ".";    // 
		boolean skipNoGeneAbstracts = false;
		Set<String> xml_files_to_ignore = new HashSet<String>();
		String filelist_f = "";
		//boolean annotateAllXml = false;
		for (int a = 0; a < args.length; a++) {
			// parameter is -v to regulate verbosity at runtime
			if (args[a].matches("\\-v=\\d+"))
				verbosity = Integer.parseInt(args[a].replaceFirst("^\\-v=(\\d+)$", "$1"));
			else if (args[a].toLowerCase().matches("\\-\\-?outdir")) 
				outDir = args[++a];
			else if (args[a].toLowerCase().matches("\\-\\-?outdir\\=.+")) 
				outDir = args[a].replaceFirst("^\\-\\-?[Oo][Uu][Tt][Dd][Ii][Rr]\\=", "");
			else if (args[a].toLowerCase().equals("-g")) 
				skipNoGeneAbstracts = true;
			else if (args[a].toLowerCase().matches("\\-\\-?i(gnore)?")) {
				String ignorefile = args[++a];
				try {
					BufferedReader br = new BufferedReader(new FileReader(ignorefile));
					String line = null;
					while ((line = br.readLine()) != null) {
						if (line.trim().length() > 0 && !line.startsWith("#"))
							xml_files_to_ignore.add(line.trim());
					}
					br.close();
					if (xml_files_to_ignore.size() > 0)
						System.err.println("#INFO ignoring " + xml_files_to_ignore.size() + " input files.");
					else
						System.err.println("#WARN list of files to ignore seems to be empty: " + ignorefile);
				} catch (FileNotFoundException e) {
					System.err.println("#ERROR could not find file " + ignorefile);
					System.exit(4);
				} catch (IOException e) {
					System.err.println("#ERROR reading from file " + ignorefile);
					System.exit(4);
				}
			} else if (args[a].toLowerCase().matches("\\-\\-?list")) {
				filelist_f = args[++a];
			//} else if (args[a].toLowerCase().matches("\\-\\-?all")) {
			//	annotateAllXml = true;
			} else {
				dir = args[a];
				File DIR = new File(dir);
				if (DIR.exists() && DIR.canRead()) {
					if (DIR.isDirectory()) {
						if (DIR.list().length == 0) {
							System.err.println("Error: there seem to be no files in the directory " + args[a]);
							System.exit(3);
						}
					} else {
						// is a single file
					}
				} else {
					System.err.println("Error: cannot access the file/directory " + args[a]);
					System.exit(2);					
				}
			}
		}
		
		
		if (dir.length() == 0 && filelist_f.length() == 0) {
			System.err.println("Please specify an input directory!");
			System.exit(2);
		} else if (dir.length() > 0) {
			File DIR = new File(dir);
			if (!DIR.exists()) // || !DIR.isDirectory()) 
				{
				System.err.println("Please specify a valid input directory!");
				System.exit(2);
			}
		}

		// create the output directory if it does not exist
		if (!outDir.equals(".")) {
			File DIR = new File(outDir);
			if (!DIR.exists())
				DIR.mkdirs();
		}
		
		//
		ConstantsNei.setOutputLevel(verbosity);

		// check the input directory for all valid files
		File DIR = new File(dir);
		String[] filelist_p = {};
		List<String> filelist = new LinkedList<String>();
		// if 'dir' indeed points to a directory:
		if (DIR.isDirectory()) {
			filelist_p = DIR.list();
			for (String filename: filelist_p) {
				if (filename.matches("\\d+\\.xml(\\.gz)?"))
					if (!xml_files_to_ignore.contains(filename))
						filelist.add(filename);
			}
		// if 'dir' seems to be a single file:
		} else {
			String filename = dir.replaceFirst("^(.+)?\\/(.+?)$", "$2"); // get file name part
			dir = dir.replaceFirst("^(.+)?\\/(.+?)$", "$1");             // get new directory name part
			if (filename.matches("\\d+\\.xml(\\.gz)?"))
				if (!xml_files_to_ignore.contains(filename))
					filelist.add(filename);
		}
		
		//
		if (filelist_f != null && filelist_f.length() > 0) {
			File LIST = new File(filelist_f);
			try {
				BufferedReader br = new BufferedReader(new FileReader(LIST));
				String line = "";
				while ((line = br.readLine()) != null) {
					if (line.trim().length() > 0 && !line.startsWith("#"))
						filelist.add(line.trim());
				}
				br.close();
			} catch (IOException ioe) {
				System.err.println("#ERROR reading file " + filelist_f + ": " + ioe.getMessage());
				System.exit(4);
			}
		}
		
		//
		if (filelist.size() == 0) {
			//System.err.println("Error: found no files matching the naming convention medline12n123.xml or .xml.gz");
			System.err.println("Error: found no files in the input.");
			System.exit(1);
		}
		
		// loop through all XML files, creating a Run each and process them individually
		int c_file = 0;
		long starttime = System.currentTimeMillis();
		for (String filename: filelist) {
			c_file++;
			if (c_file == 1)
				System.err.println("#INFO annotating " + filename + " (" + c_file + " out of " + filelist.size() + ")");
			else {
				long delta = System.currentTimeMillis() - starttime;
				//float msec_per_file  = (float)delta / (float)c_file;
				//float msec_all_files = filelist.size() * msec_per_file;
				//float min_all_files  = msec_all_files / 1000f / 60f;
				
				String time = String.format("%d min %02d sec", 
					    TimeUnit.MILLISECONDS.toMinutes(delta),
					    TimeUnit.MILLISECONDS.toSeconds(delta) - 
					    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(delta))
					);
				//System.err.println("#INFO annotating " + filename + " (" + c_file + " out of " + filelist.size() +
				//	", ETA " + min_all_files + "min");
				System.err.println("#INFO annotating " + filename + " (" + c_file + " out of " + filelist.size() +
					", ETA " + time + " (based on previously annotated file)");
			}
			
			// each pipeline is handled by a "Run"
			Run run = new Run();
			run.verbosity = verbosity;
			
			//////////
			// INPUT
			String relfilename = filename;
			if (relfilename.indexOf("/") < 0)
				relfilename = dir + "/" + relfilename;
			//System.err.println("# " + filename);
			//System.err.println("# " + relfilename);
			//run.setTextRepository(TextFactory.loadTextRepositoryFromMedlineFile(dir + "/" + filename));
			run.setTextRepository(TextFactory.loadTextRepositoryFromMedlineFile(relfilename));
			
			//////////
			// PROCESSING
			// Plug together a processing pipeline: add filters to the Run
			
			//////////
			// Pre-processing filters here:
	        boolean keepTextIntact = true;
	        String keeptemp = ISGNProperties.get("keepTextIntact");
	        if (keeptemp.toLowerCase().matches("(0|no|false)"))
	        	keepTextIntact = false;
	        if (!keepTextIntact)
	        	run.addFilter(new NameRangeExpander());

			//////////
			// NER filters here:
			// default species NER: spots human, mouse, rat, yeast, and fly only
			run.addFilter(new DefaultSpeciesRecognitionFilter());
			String assumeSpecies = ISGNProperties.get("assumeSpecies");
			if (assumeSpecies != null && assumeSpecies.length() > 0) {
				String[] species = assumeSpecies.split("[\\;\\,]\\s*");
				for (String spec: species) {
					if (!spec.matches("\\d+")) continue;
					int tax = Integer.parseInt(spec);
					for (Text text : run.getTextRepository().getTexts())
						text.addTaxonId(tax);
				}
			}
			
			// construct a dictionary for human, mouse, yeast, fruit fly genes only
			RunAllGeneDictionaries afewDictionaryFilters = new RunAllGeneDictionaries();
			afewDictionaryFilters.setLimitToTaxons(9606, 10090, 10116, 559292, 7227);
			run.addFilter(afewDictionaryFilters);
	
			//////////
			// NER post-processing filters here:
			run.addFilter(new RecognizedEntityUnifier());
	
			// include a few disambiguation filters that do not need specific information on each candidate gene
			// thus, these work on the gene's name and its context in the text
			run.addFilter(new ImmediateContextFilter());
			
			// strictFPs_2_2_context_all.object contains data on the context defined by two tokens left and two tokens right of a gene name
			//run.addFilter(new LeftRightContextFilter("data/strictFPs_2_2_context_all.object", "data/nonStrictFPs_2_2_context_all.object", 0d, 2, 2));
	
			// load the gene repository to obtain information on each gene (if only the species)
			// not loading gene repository will produce an empty result at the end
			run.addFilter(new GeneRepositoryLoader(GeneRepositoryLoader.RetrievalMethod.DATABASE));
	
			//
			run.addFilter(new StopWordFilter(ISGNProperties.get("stopWords")));
			//
			run.addFilter(new UnambiguousMatchFilter());
			//
			run.addFilter(new UnspecificNameFilter());
			//
			//run.addFilter(new AlignmentFilter(AlignmentHelper.globalAlignment, 0.7f));
			//
			run.addFilter(new NameValidationFilter());
			
			// filter by the number of occurrences of each organism
			run.addFilter(new SpeciesFrequencyFilter());
			
			// Final disambiguation filter
			run.addFilter(new MultiSpeciesDisambiguationFilter(
					Integer.parseInt(ISGNProperties.get("disambiguationThreshold")),
					Integer.parseInt(ISGNProperties.get("maxIdsForCandidatePrediction"))));
			
			// Mark everything that "survived" until here as OK, will be reported in output
			// Only for high-recall runs
			String tuning = ISGNProperties.get("tuning");
			if (tuning != null && tuning.equalsIgnoreCase("recall"))
				run.addFilter(new IdentifyAllFilter());
			
		
			//////////
			// RUN
			
			// Run all filters, affecting run.context, run.textRepository, and run.geneRepository
			run.runFilters();

			
			//////////
			// OUTPUT
			if (ConstantsNei.verbosityAtLeast(ConstantsNei.OUTPUT_LEVELS.STATUS))
				System.err.println("#Writing output file(s)...");


			// For texts belonging to a document set (one XML document with multiple texts),
			// store the annotated XML in a buffer before writing the actual XML set file(s)
			// Input can come from multiple such document set files, therefore the two maps, which also
			// store the file type (mostly to distinguish medline xml vs plain xml)
			Map<String, StringBuilder> file2buffer  = new HashMap<String, StringBuilder>();
			Map<String, Text.SourceTypes> file2type = new HashMap<String, Text.SourceTypes>();

			// loop through all texts, generate the annotated XML
			// and write the new content to file(s)
			Collection<Text> texts = run.getTextRepository().getTexts();
			// TODO the output is currently not in the order of input!
			// especially for PubmedArticleSet XML files it might be better to retain the input order
			// (within one XML file)
			for (Text text: texts) {

				System.err.println("#INFO title=" + text.title);
				
				//if (annotateAllXml)
				//	text.sourceType = Text.SourceTypes.MEDLINES_XML;
				
				// sort entities by position within each text, insert into the text from back to end
				List<RecognizedEntity> entitiesBackwards = new LinkedList<RecognizedEntity>();
				entitiesBackwards.addAll(run.context.getRecognizedEntitiesInText(text));
				Collections.sort(entitiesBackwards, new Sorting.RecognizedEntitySorter());
				Collections.reverse(entitiesBackwards);
				
				// do not print abstracts that don't have genes (command line parameter)
				if (skipNoGeneAbstracts && entitiesBackwards.size() == 0)
					continue;

				// start with the plain text ...
				String annotatedText = text.plainText;
				
				System.err.println("#INFO plaintext=" + annotatedText);
				
				// ... and insert markup for all recognized entities
				//int cRE = 0;
				for (RecognizedEntity se: entitiesBackwards) {
					//System.err.println("RE" + (++cRE));
					TextAnnotation ta = se.getAnnotation();
					ta.setType(TextAnnotation.Type.GENE);
					//System.err.print(text.getID()
					//		+ "\t" + se.entity.getBegin() + "\t" + se.entity.getEnd()
					//		+ "\t" + se.entity.getName() + "\t" + ta.getType().toString());
					IdentificationStatus idStatus = run.context.getIdentificationStatus(se);
					String geneId = idStatus.getId();
					//System.err.println("\t" + geneId + "\t" + idStatus.getIdCandidates());
					Set<String> otherIds_set = new TreeSet<String>();
					otherIds_set.addAll(idStatus.getIdCandidates());
					if (otherIds_set != null && otherIds_set.size() > 0 && geneId != null)
						otherIds_set.remove(geneId);
					String otherIds = "";
					if (otherIds_set.size() > 0)
						otherIds = StringHelper.joinStringSet(otherIds_set, ";");

					// insert the gene's ID into the XML
					// in some cases, candidate IDs with the same score are returned
					// pick the first ID and set as main ID
					// add the other IDs in a separate XML attribute
					String insert = "<";
					if (geneId == null || geneId.length() == 0) {
						annotatedText = annotatedText.substring(0, se.getEnd() + 1) + "</" + xml_tag_mention + ">" + annotatedText.substring(se.getEnd() + 1);

						insert += xml_tag_mention;
						if (otherIds.length() > 0)
							insert += " " + xml_attribute_candidate_ids + "=\"" + otherIds + "\"";
					} else {
						annotatedText = annotatedText.substring(0, se.getEnd() + 1) + "</" + xml_tag + ">" + annotatedText.substring(se.getEnd() + 1);

						insert += xml_tag;
						insert += " " + xml_attribute_id + "=\"" + geneId + "\"";
						if (otherIds.length() > 0)
							insert += " " + xml_attribute_other_ids + "=\"" + otherIds + "\"";
						// get the Gene object for the (main) gene ID
						// and from that, get the official symbol (if known)
						// and add to XML attribute
						Gene gene = run.getGene(geneId);
						String symbol = "";
						if (gene != null && gene.officialSymbol != null && gene.officialSymbol.length() > 0)
							symbol = gene.officialSymbol;
						if (symbol.matches("\\s*[\\;\\,]\\s*"))
							symbol = symbol.split("\\s*[\\,\\;]\\s*")[0].trim();
						if (symbol.length() > 0)
							insert += " " + xml_attribute_symbol + "=\"" + gene.officialSymbol + "\"";
						if (gene.getTaxon() > 0)
							insert += " " + xml_attribute_species + "=\"" + gene.getTaxon() + "\"";

						float score = run.context.getConfidenceScore(gene, text.ID);
						if (score >= 0.0)
							insert +=  " " + xml_attribute_score + "=\"" + score + "\"";
					}

					insert += ">";
					annotatedText = annotatedText.substring(0, se.getBegin()) + insert + annotatedText.substring(se.getBegin());
				}

				System.err.println("#INFO annotated=" + annotatedText);
				//System.err.println("#Annotated text:\n"+annotatedText+"\n----------");

				//System.err.println("#INFO sourceType=" + text.sourceType.toString());
				
				//if (entitiesBackwards.size() == 0) {
				//	if (ConstantsNei.verbosityAtLeast(ConstantsNei.OUTPUT_LEVELS.WARNINGS))
				//		ConstantsNei.OUT.println("Found no genes in text " + text.getPMID());
				//}

				if (text.sourceType == Text.SourceTypes.PLAIN) {
					System.err.println("---PLAIN");
					//System.err.println("#text="+text.getID() + ", source="+text.sourceType.toString());
					annotatedText = "<text id=\"" + text.getID() + "\">\n" + annotatedText + "\n</text>";
					text.annotatedXml = annotatedText;

				} else {//if (text.sourceType == Text.SourceTypes.MEDLINE_XML || text.sourceType == Text.SourceTypes.MEDLINES_XML) {
					System.err.println("---MEDLINE");
					// get the first sentence from the text, by finding the first sentence end mark
					// assumes it is the full title of the paper
					// TODO better to store (and annotate!) the title separately, since some titles consist of multiple sentences
					String[] multilines = annotatedText.split("[\r\n]+");
					//if (multilines.length > 0) System.err.println("#INFO multiple lines");
	
					String annotatedTitle = "";
					String annotatedTextWithoutTitle  = "";
					if (multilines.length > 0) {
						annotatedTitle = multilines[0].replaceFirst("^(.+?[\\.\\!\\?])\\s.*$", "$1");
						StringBuilder b = new StringBuilder();
						b.append(multilines[0].replaceFirst("^.+?[\\.\\!\\?]\\s(.*)$", "$1"));
						b.append("\n");
						for (int l = 1; l < multilines.length; l++) {
							//System.out.println("#INFO appending " + multilines[l]);
							b.append(multilines[l] + "\n");
						}
						annotatedTextWithoutTitle = b.toString();
					} else {
						annotatedTitle = annotatedText.replaceFirst("^(.+?[\\.\\!\\?])\\s.*$", "$1");
						// assume the 2nd and following sentences are the abstract
						annotatedTextWithoutTitle = annotatedText.replaceFirst("^.+?[\\.\\!\\?]\\s(.*)$", "$1");
					}
					
					System.err.println("#INFO annotatedTextWithoutTitle = " + annotatedTextWithoutTitle);
					
					text.annotateXmlTitle(annotatedTitle);
					text.annotateXmlAbstract(annotatedTextWithoutTitle);
				//} else {
				//	System.err.println("---HELLO?");
				}

				// if the XML tag used to mark gene names has a prefix ("prefix:TAG"), we need to bind this prefix
				if (xml_tag.indexOf(":") > 0) {
					String prefix = xml_tag.replaceFirst("^(.+?)\\:.*$", "$1"); 
					text.addPrefixToXml(prefix);
				}

				//
				text.buildJDocumentFromAnnotatedXml();

				// write annotated text to file:
				// either into one or more document sets (file(s) with more than one text) ...
				if (text.sourceType == Text.SourceTypes.MEDLINES_XML) {
					String basefilename = text.filename.replaceFirst("^(.*)\\/([^\\/]+?)$", "$2");
					if (basefilename.endsWith(".xml.gz"))
						basefilename = basefilename.replaceFirst("\\.xml\\.gz$", ".annotated.xml");
					else if (basefilename.endsWith(".xml"))
						basefilename = basefilename.replaceFirst("\\.xml$", ".annotated.xml");
					else
						basefilename = basefilename.replaceFirst(".medline", ".annotated.medline");

					String x = text.toXmlString();
					System.err.println("#INFO x = " + x);
					// texts that are part of a collection within one file get stored
					// in a buffer for that file; we're storing this buffer in memory
					// and write it to disk, together with appropriate XML root elements,
					// once all texts were processed here
					if (file2buffer.containsKey(basefilename)) {
						StringBuilder buf = file2buffer.get(basefilename);
						buf.append(x);
						//buf.append("\n");
						file2buffer.put(basefilename, buf);
					} else {
						StringBuilder buf = new StringBuilder();
						buf.append(x);
						//buf.append("\n");
						file2buffer.put(basefilename, buf);
						file2type.put(basefilename, Text.SourceTypes.MEDLINES_XML);
					}
					// ... or individually
				} else {
					// we write individual files directly to the disk:
					String outfileName = text.getID() + ".annotated.xml";
					//if (outDir.length() > 0) text.toXmlFile(outfileName);
					//else text.toXmlFile(outDir + "/" + outfileName);
					if (outDir.length() == 0) text.toXmlFile(outfileName);
					else text.toXmlFile(outDir + "/" + outfileName);
				}

				// or, if no gene was recognized in the current text:
				//} else {
				//	Text aText = run.getTextRepository().getText(text_id);
				//	String outfileName = aText.getID() + "_nogenesfound.annotated.xml";
				//	aText.toXmlFile(outDir + "/" + outfileName);
				//}


			} // foreach text



			if (file2buffer.size() > 0) {
				//System.err.println("#Writing file collections");
				for (String basefilename: file2buffer.keySet()) {
					StringBuilder buf = file2buffer.get(basefilename);
					Text.SourceTypes type = file2type.get(basefilename);

					String outfile = basefilename;
					if (outDir.length() > 0)
						outfile = outDir + "/" + outfile;

					try {
						BufferedWriter bw = new BufferedWriter(new FileWriter(outfile));
						if (type == Text.SourceTypes.PUBMEDS_XML)
							bw.write("<?xml version=\"1.0\"?>\n" + 
									"<!DOCTYPE PubmedArticleSet PUBLIC \"-//NLM//DTD PubMedArticle, 1st January 2015//EN\" \"http://www.ncbi.nlm.nih.gov/corehtml/query/DTD/pubmed_150101.dtd\">\n" +
							"<PubmedArticleSet>\n");
						else if (type == Text.SourceTypes.MEDLINES_XML)
							bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + 
									"<!DOCTYPE MedlineCitationSet PUBLIC \"-//NLM//DTD Medline Citation, 1st January 2015//EN\" \"http://www.nlm.nih.gov/databases/dtd/nlmmedlinecitationset_150101.dtd\">\n" +
							"<MedlineCitationSet>\n");
						else
							bw.write("<?xml version=\"1.0\"?>\n" +
							"<DocumentSet>\n");

						bw.write(buf.toString());

						if (type == Text.SourceTypes.PUBMEDS_XML)
							bw.write("\n</PubmedArticleSet>");
						else if (type == Text.SourceTypes.MEDLINES_XML)
							bw.write("\n</MedlineCitationSet>");
						else
							bw.write("\n</DocumentSet>");

						bw.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}
			} // for each input file from which texts were taken (here: always 1) 
			
			// haha
			System.gc();

		} // for each Medline XML file
		
	}
	
}
