package net.sourceforge.ondex.parser.fingerprints;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.sourceforge.ondex.args.ArgumentDefinition;
import net.sourceforge.ondex.args.FileArgumentDefinition;
import net.sourceforge.ondex.core.ConceptClass;
import net.sourceforge.ondex.core.DataSource;
import net.sourceforge.ondex.core.EvidenceType;
import net.sourceforge.ondex.core.ONDEXConcept;
import net.sourceforge.ondex.core.ONDEXRelation;
import net.sourceforge.ondex.core.RelationType;
import net.sourceforge.ondex.event.type.GeneralOutputEvent;
import net.sourceforge.ondex.event.type.InconsistencyEvent;
import net.sourceforge.ondex.parser.ONDEXParser;

/**
 * Parser for fingerprint files generated by OpenBabel in their fingerprint file
 * format. Each fingerprint category will become a concept and each compound
 * will get linked to it via a is_a relation and tags. Currently supports only
 * ChEMBL database identifiers.
 * 
 * @author taubertj
 * 
 */
public class Parser extends ONDEXParser implements MetaData {

	DataSource dsCHEMBL;

	DataSource dsVO;

	EvidenceType evidencetype;

	ConceptClass ccComp;

	ConceptClass ccThing;

	RelationType rtIsa;

	@Override
	public ArgumentDefinition<?>[] getArgumentDefinitions() {
		return new ArgumentDefinition<?>[] { new FileArgumentDefinition(
				FileArgumentDefinition.INPUT_FILE,
				FileArgumentDefinition.INPUT_FILE_DESC, true, true, false) };
	}

	@Override
	public String getId() {
		return "fingerprints";
	}

	@Override
	public String getName() {
		return "OpenBabel FingerPrints Reader";
	}

	@Override
	public String getVersion() {
		return "31.10.2011";
	}

	/**
	 * initialise Ondex meta-data
	 */
	private void initMetaData() {

		// basic concept meta data
		dsCHEMBL = graph.getMetaData().getDataSource(DS_CHEMBL);
		dsVO = graph.getMetaData().getDataSource(DS_VO);
		ccComp = graph.getMetaData().getConceptClass(CC_COMP);
		ccThing = graph.getMetaData().getConceptClass(CC_THING);
		evidencetype = graph.getMetaData().getEvidenceType(ET_IMPD);
		rtIsa = graph.getMetaData().getRelationType(RT_ISA);
	}

	@Override
	public String[] requiresValidators() {
		return new String[0];
	}

	@Override
	public void start() throws Exception {
		// setup meta data
		initMetaData();

		// file name of file to parse
		File file = new File(
				(String) args.getUniqueValue(FileArgumentDefinition.INPUT_FILE));
		fireEventOccurred(new GeneralOutputEvent("Reading: "
				+ file.getAbsolutePath(), getCurrentMethodName()));

		// open file as stream, handle compressed files
		InputStream inputStream = new FileInputStream(file);
		if (file.getAbsolutePath().endsWith(".gz")) {
			inputStream = new GZIPInputStream(inputStream);
		}

		// setup reader
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				inputStream));

		// already created categories of fingerprints
		Map<String, ONDEXConcept> categories = new HashMap<String, ONDEXConcept>();

		String id = null;

		// read file
		while (reader.ready()) {
			String line = reader.readLine();

			if (line.startsWith(">")) {
				// should be a CHEMBL ID
				if (!line.contains("CHEMBL")) {
					fireEventOccurred(new InconsistencyEvent(
							"Non-CHEMBL ID found: " + id,
							getCurrentMethodName()));
					// skip to next entry
					reader.readLine();
				} else
					id = line.substring(line.indexOf("CHEMBL"));
			} else {
				// create compound once
				ONDEXConcept comp = graph.getFactory().createConcept(id,
						dsCHEMBL, ccComp, evidencetype);
				comp.createConceptAccession(id, dsCHEMBL, false);

				// split tab separated categories
				for (String s : line.trim().split("\t")) {

					// check that a category exists
					if (s.trim().length() == 0) {
						fireEventOccurred(new InconsistencyEvent(
								"Empty category for: " + id,
								getCurrentMethodName()));
						continue;
					}

					// categories are cached
					if (!categories.containsKey(s)) {
						// new category
						ONDEXConcept c = graph.getFactory().createConcept(s,
								dsVO, ccThing, evidencetype);
						c.createConceptName(s, true);
						c.addTag(c); // self-tag
						categories.put(s, c);
					}

					// get current category
					ONDEXConcept cat = categories.get(s);

					// add as tag to compound
					comp.addTag(cat);

					// create relation between category and compound
					ONDEXRelation r = graph.getFactory().createRelation(comp,
							cat, rtIsa, evidencetype);
					r.addTag(cat);
				}
			}
		}

		// close reader
		reader.close();
	}

}