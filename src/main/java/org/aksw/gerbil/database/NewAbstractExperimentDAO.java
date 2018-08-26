package org.aksw.gerbil.database;

import java.util.ArrayList;
import java.util.List;

import org.aksw.gerbil.datatypes.ErrorTypes;
import org.aksw.gerbil.datatypes.ExperimentTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

/**
 * Abstract class implementing the general behavior of an {@link NewExperimentDAO}.
 * Note that it is strongly recommended to extend this class instead of
 * implementing the {@link NewExperimentDAO} class directly since this class
 * already takes care of the synchronization problem of the
 * {@link NewExperimentDAO#connectCachedResultOrCreateTask(Model, Resource, Resource, Resource, Resource)} method.
 
 **/

public abstract class NewAbstractExperimentDAO implements NewExperimentDAO {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NewExperimentDAOImpl.class);

    /**
     * Sentinel value used to indicate that an experiment task with the given
     * preferences couldn't be found.
     */
    protected static final int EXPERIMENT_TASK_NOT_CACHED = -1;

    protected long resultDurability;
    protected boolean initialized = false;

    public NewAbstractExperimentDAO() {
    }

    public NewAbstractExperimentDAO(long resultDurability) {
        setResultDurability(resultDurability);
    }
    
    /**
     * The {@link AbstractExperimentDAO} class makes sure that an instance is
     * initialized only once even if this method is called multiple times.
     */
    @Override
    public void initialize() {
        if (!initialized) {
            /*
             * We only have to set back the status of experiments that were
             * running while the server has been stopped.
             */
            setRunningExperimentsToError();
        }
    }

    /**
     * Searches the database for experiment tasks that have been started but not
     * ended yet (their status equals {@link #TASK_STARTED_BUT_NOT_FINISHED_YET} ) and set their status to
     * {@link ErrorTypes#SERVER_STOPPED_WHILE_PROCESSING}. This method should
     * only be called directly after the initialization of the database. It
     * makes sure that "old" experiment tasks which have been started but never
     * finished are set to an error state and can't be used inside the caching
     * mechanism.
     */
    protected abstract void setRunningExperimentsToError();

    @Override
    public void setResultDurability(long resultDurability) {
        this.resultDurability = resultDurability;
    }

    public long getResultDurability() {
        return resultDurability;
    }

    
    public synchronized Resource connectCachedResultOrCreateTask(Model model, Resource annotatorName, Resource datasetName,
            Resource experimentType, Resource matching, Resource experiment) {
        Resource experimentTask;
    	experimentTask.addLiteral(RDF.value,EXPERIMENT_TASK_NOT_CACHED);
        if (resultDurability > 0) {
            experimentTask = getCachedExperimentTaskId1(model,annotatorName,datasetName,experimentType,matching,experiment);
        } else {
            LOGGER.warn("The durability of results is <= 0. I won't be able to cache results.");
        }
        if (experimentTask == EXPERIMENT_TASK_NOT_CACHED) {
            return createExperimentTask(model,annotatorName, datasetName, experimentType, matching, experiment);
        } else {
            LOGGER.debug("Could reuse cached task (id={}).", experimentTask);
            connectExistingTaskWithExperiment(model,experimentTask, experiment);
            return CACHED_EXPERIMENT_TASK_CAN_BE_USED;
        }
    }
        /**
         * The method checks whether there exists an experiment task with the given
         * preferences inside the database. If such a task exists, if it is not to
         * old regarding the durability of experiment task results and if its state
         * is not an error code, its experiment task id is returned. Otherwise {@link #EXPERIMENT_TASK_NOT_CACHED} is
         * returned.
         * 
         * <b>NOTE:</b> this method MUST be synchronized since it should only be
         * called by a single thread at once.
         * 
         * @param annotatorName
         *            the name with which the annotator can be identified
         * @param datasetName
         *            the name of the dataset
         * @param experimentType
         *            the name of the experiment type
         * @param matching
         *            the name of the matching used
         * @return The id of the experiment task or {@value #EXPERIMENT_TASK_NOT_CACHED} if such an experiment task
         *         couldn't be found.
         */
       protected abstract Resource getCachedExperimentTaskId1(Model model, Resource annotatorName, Resource datasetName, Resource experimentType,
	            Resource matching, Resource experiment);

        /**
         * This method connects an already existing experiment task with an
         * experiment.
         * 
         * @param experimentTaskId
         *            the id of the experiment task
         * @param experimentId
         *            the id of the experiment
         */
        protected abstract void connectExistingTaskWithExperiment(Model model, Resource experimentTask, Resource experiment);

    	

}
