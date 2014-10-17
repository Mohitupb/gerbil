package org.aksw.gerbil.database;

import java.util.List;

import javax.sql.DataSource;

import org.aksw.gerbil.datatypes.ExperimentTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 
 * @author b.eickmann
 * 
 */
public class ExperimentDAOImpl extends AbstractExperimentDAO {

    private final static String INSERT_TASK = "INSERT INTO ExperimentTasks (annotatorName, datasetName, experimentType, matching, state, lastChanged) VALUES (:annotatorName, :datasetName, :experimentType, :matching, :state, :lastChanged)";
    private final static String SET_TASK_STATE = "UPDATE ExperimentTasks SET state=:state, lastChanged=:lastChanged WHERE id=:id";
    private final static String SET_EXPERIMENT_TASK_RESULT = "UPDATE ExperimentTasks SET microF1=:microF1 , microPrecision=:microPrecision, microRecall=:microRecall, macroF1=:macroF1, macroPrecision=:macroPrecision, macroRecall=:macroRecall, errorCount=:errorCount, lastChanged=:lastChanged WHERE id=:id";
    private final static String CONNECT_TASK_EXPERIMENT = "INSERT INTO Experiments (id, taskId) VALUES(:id, :taskId)";
    private final static String GET_TASK_STATE = "SELECT state FROM ExperimentTasks WHERE id=:id";
    private final static String GET_EXPERIMENT_RESULTS = "SELECT annotatorName, datasetName, experimentType, matching, microF1, microPrecision, microRecall, macroF1, macroPrecision, macroRecall, state, errorCount, lastChanged FROM ExperimentTasks t, Experiments e WHERE e.id=:id AND e.taskId=t.id";
    private final static String GET_CACHED_TASK = "SELECT id FROM ExperimentTasks WHERE annotatorName=:annotatorName AND datasetName=:datasetName AND experimentType=:experimentType AND matching=:matching AND lastChanged>:lastChanged ORDER BY lastChanged DESC LIMIT 1";
    private final static String GET_HIGHEST_EXPERIMENT_ID = "SELECT id FROM Experiments ORDER BY id DESC LIMIT 1";

    private final NamedParameterJdbcTemplate template;

    public ExperimentDAOImpl(DataSource dataSource) {
        this.template = new NamedParameterJdbcTemplate(dataSource);
    }

    public ExperimentDAOImpl(DataSource dataSource, long resultDurability) {
        super(resultDurability);
        this.template = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public List<ExperimentTaskResult> getResultsOfExperiment(String experimentId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", experimentId);
        List<ExperimentTaskResult> result = this.template.query(GET_EXPERIMENT_RESULTS, parameters,
                new ExperimentTaskResultRowMapper());
        return result;
    }

    @Override
    public int createTask(String annotatorName, String datasetName, String experimentType, String matching,
            String experimentId) {
        MapSqlParameterSource params = createTaskParameters(annotatorName, datasetName, experimentType, matching);
        params.addValue("state", ExperimentDAO.TASK_STARTED_BUT_NOT_FINISHED_YET);
        java.util.Date today = new java.util.Date();
        params.addValue("lastChanged", new java.sql.Timestamp(today.getTime()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        this.template.update(INSERT_TASK, params, keyHolder);
        Integer generatedKey = (Integer) keyHolder.getKey();
        connectToExperiment(experimentId, generatedKey);
        return generatedKey;
    }

    private void connectToExperiment(String experimentId, Integer taskId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", experimentId);
        parameters.addValue("taskId", taskId);
        this.template.update(CONNECT_TASK_EXPERIMENT, parameters);
    }

    private MapSqlParameterSource createTaskParameters(String annotatorName, String datasetName, String experimentType,
            String matching) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("annotatorName", annotatorName);
        parameters.addValue("datasetName", datasetName);
        parameters.addValue("experimentType", experimentType);
        parameters.addValue("matching", matching);
        return parameters;
    }

    @Override
    public void setExperimentTaskResult(int experimentTaskId, ExperimentTaskResult result) {
        // Note that we have to set the state first if we want to override the automatic timestamp with the one from the
        // result object
        setExperimentState(experimentTaskId, result.state);

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", experimentTaskId);
        parameters.addValue("microF1", result.getMicroF1Measure());
        parameters.addValue("microPrecision", result.getMicroPrecision());
        parameters.addValue("microRecall", result.getMicroRecall());
        parameters.addValue("macroF1", result.getMacroF1Measure());
        parameters.addValue("macroPrecision", result.getMacroPrecision());
        parameters.addValue("macroRecall", result.getMacroRecall());
        parameters.addValue("errorCount", result.getErrorCount());
        parameters.addValue("lastChanged", new java.sql.Timestamp(result.timestamp));

        this.template.update(SET_EXPERIMENT_TASK_RESULT, parameters);
    }

    @Override
    public void setExperimentState(int experimentTaskId, int state) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", experimentTaskId);
        parameters.addValue("state", state);
        java.util.Date today = new java.util.Date();
        parameters.addValue("lastChanged", new java.sql.Timestamp(today.getTime()));
        this.template.update(SET_TASK_STATE, parameters);
    }

    @Override
    public int getExperimentState(int experimentTaskId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", experimentTaskId);
        List<Integer> result = this.template.query(GET_TASK_STATE, parameters, new IntegerRowMapper());
        if (result.size() > 0) {
            return result.get(0);
        } else {
            return TASK_NOT_FOUND;
        }
    }

    @Override
    protected int getCachedExperimentTaskId(String annotatorName, String datasetName, String experimentType,
            String matching) {
        MapSqlParameterSource params = createTaskParameters(annotatorName, datasetName, experimentType, matching);
        java.util.Date today = new java.util.Date();
        params.addValue("lastChanged", new java.sql.Timestamp(today.getTime() - this.resultDurability));
        List<Integer> result = this.template.query(GET_CACHED_TASK, params, new IntegerRowMapper());
        if (result.size() > 0) {
            return result.get(0);
        } else {
            return EXPERIMENT_TASK_NOT_CACHED;
        }
    }

    @Override
    protected void connectExistingTaskWithExperiment(int experimentTaskId, String experimentId) {
        connectToExperiment(experimentId, experimentTaskId);
    }

    @Override
    public String getHighestExperimentId() {
        List<String> result = this.template.query(GET_HIGHEST_EXPERIMENT_ID, new StringRowMapper());
        if (result.size() > 0) {
            return result.get(0);
        } else {
            return null;
        }
    }

}