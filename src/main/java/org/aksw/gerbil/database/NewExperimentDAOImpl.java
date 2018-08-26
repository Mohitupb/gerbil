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

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;

/* SPARQL database based implementaion of the {@link NewAbstractExperimentDAO} cass */

public class NewExperimentDAOImpl extends NewAbstractExperimentDAO {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NewExperimentDAOImpl.class);
	
	@prefix gerbil: <http://w3id.org/gerbil/vocab#> ;
	
	private final static String GET_SUB_TASK_RESULTS = "SELECT ?annotatorName ?datasetName ?experimentType ?matching ?microF1 ?microPrecision ?microRecall ?macroF1 ?macroPrecision ?macroRecall ?statusCode ?errorCount ?subExperimentof WHERE {?experiments gerbi:experimentTask ?experimentTask ?experiment gerbil:subExperimentof ?subExperiment}";
	private final static String GET_RUNNING_EXPERIMENT_TASKS = "SELECT ?annotatorName ?datasetName ?experimentType ?matching ?microF1 ?microPrecision ?microRecall ?macroF1 ?macroPrecision ?macroRecall ?statusCode ?errorCount WHERE {?ExperimentTasks gerbil:state "unfinishedState"}";
	private final static String GET_ADDITIONAL_RESULTS =  "SELECT ?version WHERE {?experiment gerbil:version ?version }";
	private final static String GET_HIGHEST_EXPERIMENT_ID = "SELECT ?experiment ORDER BY DESC(?experiment) LIMIT 1";
	private final static String GET_TASK_STATE = "SELECT ?state WHERE { ?experiment gerbil:experimentTask ?experimentTask . }";
	
		
	 public List<ExperimentTaskResult> getResultsOfExperiment(Model model,Resource experiment) {
	    	       
	        model.add(experiment,GERBIL.Experiment , experiment);
	        Query query = QueryFactory.create(GET_EXPERIMENT_RESULTS); 
	       ResultSet qExe = (ResultSet) QueryExecutionFactory.create(query, model).execSelect();
	         List<ExperimentTaskResult> result = ResultSetFormatter.outputAsJson(qExe);
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
	       
	        this.template.update(INSERT_TASK,model);
	        
	        if (experiment != null) {
	            connectToExperiment(model,experiment,experimentTask);
	        }
	        
	 }
	 
	 private void connectToExperiment(Model model,Resource experiment, Resource experimentTask) {
	    	model.add(experiment,GERBIL.Experiment,experiment);
	        model.add(experiment,GERBIL.ExperimentTask,experimentTask);
	        Query query = QueryFactory.create(CONNECT_TASK_EXPERIMENT)); 
	        QueryExecution qExe = QueryExecutionFactory.create(query, model);
	        
	    }
	 
	 public void setExperimentTaskResult(Model model,Resource experimentTask, ExperimentTaskResult result) {
	    	
	    	setExperimentState(model,experimentTask, result.state);
	    	
	    	model.add(experimentTask, GERBIL.microF1,
                 model.createTypedLiteral(String.valueOf(result.getMicroF1Measure()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.microPrecision,
                 model.createTypedLiteral(String.valueOf(result.getMicroPrecision()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.microRecall,
                 model.createTypedLiteral(String.valueOf(result.getMicroRecall()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.macroF1,
                 model.createTypedLiteral(String.valueOf(result.getMacroF1Measure()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.macroPrecision,
                 model.createTypedLiteral(String.valueOf(result.getMacroPrecision()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.macroRecall,
                 model.createTypedLiteral(String.valueOf(result.getMacroRecall()), XSDDatatype.XSDdecimal));
         model.add(experimentTask, GERBIL.errorCount, model.createTypedLiteral(String.valueOf(result.errorCount)));
         
         if (result.hasAdditionalResults()) {
             IntDoubleOpenHashMap additionalResults = result.getAdditionalResults();
           
             for (int i = 0; i < additionalResults.allocated.length; ++i) {
                 if (additionalResults.allocated[i]) {
                 	addAdditionaResult(experimentTask,result.additionalResults.keys[i], result.additionalResults.values[i]);
                 }
             }
         }
	    }
	 
	 protected void addAdditionaResult(Resource experimentTask, int resultId, double value) {
   	  model.add(experimentTask, GERBIL.Experiment, experimentTask);
         model.add("resultId", resultId);
         model.add("value", value);
         this.template.update(INSERT_ADDITIONAL_RESULT, model);
   }

   public void setExperimentState(Model model, Resource experimentTask, int state) {
       model.add(experimentTask, GERBIL.Experiment, experimentTask);
       model.add(experimentTask,GERBIL.statusCode,state);
       Calendar cal = Calendar.getInstance();
       model.add(experimentTask, GERBIL.timestamp, model.createTypedLiteral(cal));
       this.template.update(SET_TASK_STATE, model);
   }
   
   public Resource getExperimentState(Model model, Resource experimentTask) {
       
       model.add(experimentTask,GERBIL.ExperimentTask, experimentTask);
		Query query = QueryFactory.create(GET_TASK_STATE); 
		QueryExecution qExe = QueryExecutionFactory.create(query, model);
		Query query = QueryFactory.create(GET_TASK_STATE); 
		QueryExecution qExe = QueryExecutionFactory.create(query, model);
		ResultSet resultsRes = qExe.execSelect();
		
       if (resultsRes.size() > 0) {
           return result.get(0);
       } else {
           return TASK_NOT_FOUND;
       }
   }
   

   protected Resource getCachedExperimentTaskId(Model model, Resource annotatorName, Resource datasetName, Resource experimentType,
           Resource matching,Resource experiment) {
   	 model.add(experiment,GERBIL.annotator,annotatorName);
	        model.add(experiment,GERBIL.dataset,datasetName);
	        model.add(experiment,GERBIL.experimentType,experimentType);
	       	model.add(experiment,GERBIL.matching,matching);
	       	Calendar cal = Calendar.getInstance();
	        model.add(experimentTask, GERBIL.timestamp, model.createTypedLiteral(cal));
       model.add(experimentTask, GERBIL.statusCode, ErrorTypes.HIGHEST_ERROR_CODE);
       List<Integer> result = this.template.query(GET_CACHED_TASK,model);
       if (result.size() > 0) {
           return result.get(0);
       } else {
           return EXPERIMENT_TASK_NOT_CACHED;
       }
   }
   
      
   public String getHighestExperimentId() {
	   Query query = QueryFactory.create(s2); 
	   QueryExecution qExe = QueryExecutionFactory.create(query, model);
	   ResultSet resultsRes = qExe.execSelect();
	  // List<String> result = this.template.query(GET_HIGHEST_EXPERIMENT_ID, new StringRowMapper());
       if (result.size() > 0) {
           return result.get(0);
       } else {
           return null;
       }
   }
	 
   protected void connectExistingTaskWithExperiment(Model model, Resource experimentTask, Resource experiment) {
       connectToExperiment(model, experimentTask, experiment);
   }
   
   protected void setRunningExperimentsToError(Model model, Resource experiment) {
       
       model.add(experiment, GERBIL.statusCode, NewExperimentDAO.TASK_STARTED_BUT_NOT_FINISHED_YET);
       model.add(experiment"state", ErrorTypes.SERVER_STOPPED_WHILE_PROCESSING.getErrorCode());
       Calendar cal = Calendar.getInstance();
       model.add(experimentTask, GERBIL.timestamp, model.createTypedLiteral(cal));
       this.template.update(SET_UNFINISHED_TASK_STATE,model);
   }
   
   public List<ExperimentTaskResult> getAllRunningExperimentTasks() {
       MapSqlParameterSource params = new MapSqlParameterSource();
       params.addValue("unfinishedState", TASK_STARTED_BUT_NOT_FINISHED_YET);
       Query query = QueryFactory.create(GET_RUNNING_EXPERIMENT_TASKS); 
       QueryExecution qExe = QueryExecutionFactory.create(query, model);
       
   }
   
   public List<ExperimentTaskResult> getLatestResultsOfExperiments(Model model, Resource experimentType, Resource matching) {
       Resource experimentTask;
       model.add(experimentTask,GERBIL.experimentType,experimentType);
       model.add(experimentTask,GERBIL.matching,matching);
       model.add("unfinishedState", TASK_STARTED_BUT_NOT_FINISHED_YET);
       List<ExperimentTaskResult> results = this.template.query(GET_LATEST_EXPERIMENT_TASK_RESULTS,model);

       for (ExperimentTaskResult result : results) {
           addAdditionalResults(result);
       }
       return results;
   }
   
   public List<ExperimentTaskResult> getLatestResultsOfExperiments(Model model, Resource experimentType, Resource matching,
           Resource annotatorNames[], Resource datasetNames[]) {
			Resource experimentTask;
       model.add(experimentTask,GERBIL.experimentType,experimentType);
       model.add(experimentTask,GERBIL.matching,matching);
       model.add("unfinishedState", TASK_STARTED_BUT_NOT_FINISHED_YET);
       model.add(experimentTask,GERBIL.annotatorName, Arrays.asList(annotatorNames));
       model.add(experimentTask,GERBIL.datasetNames, Arrays.asList(datasetNames));
       List<ExperimentTaskResult> results = this.template.query(GET_LATEST_EXPERIMENT_TASK_RESULTS, model);

       for (ExperimentTaskResult result : results) {
           addAdditionalResults(result);
       }
       return results;
   }
	}

	    