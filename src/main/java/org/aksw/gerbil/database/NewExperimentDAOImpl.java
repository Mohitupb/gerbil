package org.aksw.gerbil.database;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.aksw.gerbil.config.GerbilConfiguration;
import org.aksw.gerbil.database.ResultNameToIdMapping;
import org.aksw.gerbil.datatypes.ErrorTypes;
import org.aksw.gerbil.datatypes.ExperimentTaskResult;
import org.aksw.gerbil.semantic.vocabs.CUBE;
import org.aksw.gerbil.semantic.vocabs.GERBIL;
import org.aksw.gerbil.web.ExperimentTaskStateHelper;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import com.carrotsearch.hppc.IntDoubleOpenHashMap;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelCon;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.aksw.gerbil.dataid.DataIDGenerator;


public class NewExperimentDAOImpl extends NewAbstractExperimentDAO {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NewExperimentDAOImpl.class);

	
	 public List<ExperimentTaskResult> getResultsOfExperiment(Model model,Resource experiment) {
	    	       
	        model.add(experiment,GERBIL.Experiment , experiment);
	         List<ExperimentTaskResult> result = this.template.query(GET_EXPERIMENT_RESULTS, parameters);
	             for (ExperimentTaskResult e : result) {
	            addAdditionalResults(e);
	            addSubTasks(e);
	        }
	        return result;
	    }
	 
	 public void createTask(Model model, Resource annotatorName, Resource datasetName, Resource experimentType, Resource matching,
	            Resource experiment) {
		    model.add(experiment,GERBIL.annotator,annotatorName);
	        model.add(experiment,GERBIL.dataset,datasetName);
	        model.add(experiment,GERBIL.experimentType,experimentType);
	       	model.add(experiment,GERBIL.matching,matching);
	        model.add(experiment,GERBIL.statusCode, NewExperimentDAO.TASK_STARTED_BUT_NOT_FINISHED_YET);
	        Calendar cal = Calendar.getInstance();
	        model.add(experiment, GERBIL.timestamp, model.createTypedLiteral(cal));
	       
	        this.template.update(INSERT_TASK, model);
	        
	        if (experiment != null) {
	            connectToExperiment(model,experiment,experimentTask);
	        }
	        
	 }
	 
	 private void connectToExperiment(Model model,Resource experiment, Resource experimentTask) {
	    	model.add(experiment,GERBIL.Experiment,experiment);
	        model.add(experiment,GERBIL.ExperimentTask,experimentTask);
	        this.template.update(CONNECT_TASK_EXPERIMENT, parameters);
	    }
	 
	}

	    