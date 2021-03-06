/*
 * Copyright (C) 2017-2018 Stichting Akvo (Akvo Foundation)
 *
 * This file is part of Akvo Flow.
 *
 * Akvo Flow is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Akvo Flow is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Akvo Flow.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.akvo.flow.data.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.squareup.sqlbrite2.BriteDatabase;

import org.akvo.flow.data.migration.FlowMigrationListener;
import org.akvo.flow.data.migration.languages.MigrationLanguageMapper;
import org.akvo.flow.data.preference.Prefs;
import org.akvo.flow.database.RecordColumns;
import org.akvo.flow.database.ResponseColumns;
import org.akvo.flow.database.SurveyColumns;
import org.akvo.flow.database.SurveyDbAdapter;
import org.akvo.flow.database.SurveyGroupColumns;
import org.akvo.flow.database.SurveyInstanceColumns;
import org.akvo.flow.database.SurveyInstanceStatus;
import org.akvo.flow.database.TransmissionColumns;
import org.akvo.flow.database.TransmissionStatus;
import org.akvo.flow.database.britedb.BriteSurveyDbAdapter;
import org.akvo.flow.domain.FileTransmission;
import org.akvo.flow.domain.QuestionResponse;
import org.akvo.flow.domain.Survey;
import org.akvo.flow.domain.SurveyGroup;
import org.akvo.flow.domain.User;
import org.akvo.flow.util.ConstantUtil;
import org.akvo.flow.util.PlatformUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Temporary class to access SurveyDb from the app without having to refactor the whole architecture
 */
public class SurveyDbDataSource {

    private final SurveyDbAdapter surveyDbAdapter;
    private final BriteSurveyDbAdapter briteSurveyDbAdapter;
    private final SurveyMapper surveyMapper = new SurveyMapper();

    @Inject
    public SurveyDbDataSource(Context context, BriteDatabase briteDatabase) {
        this.briteSurveyDbAdapter = new BriteSurveyDbAdapter(briteDatabase);
        this.surveyDbAdapter = new SurveyDbAdapter(context,
                new FlowMigrationListener(new Prefs(context),
                        new MigrationLanguageMapper(context)));
    }

    /**
     * Open or create the briteSurveyDbAdapter
     *
     * @throws SQLException if the database could be neither opened or created
     */
    public SurveyDbAdapter open() throws SQLException {
        return surveyDbAdapter.open();
    }

    /**
     * close the db
     */
    public void close() {
        surveyDbAdapter.close();
    }

    public Map<String, QuestionResponse> getResponses(long surveyInstanceId) {
        Map<String, QuestionResponse> responses = new HashMap<>();

        Cursor cursor = surveyDbAdapter.getResponses(surveyInstanceId);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                QuestionResponseColumns columns = new QuestionResponseColumns(cursor);
                do {
                    QuestionResponse response = getQuestionResponseBuilder(cursor,columns)
                            .setSurveyInstanceId(surveyInstanceId)
                            .createQuestionResponse();
                    responses.put(response.getResponseKey(), response);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return responses;
    }

    private QuestionResponse.QuestionResponseBuilder getQuestionResponseBuilder(Cursor cursor,
          QuestionResponseColumns columns) {
        return new QuestionResponse.QuestionResponseBuilder()
                .setValue(cursor.getString(columns.getAnswerColumn()))
                .setType(cursor.getString(columns.getTypeColumn()))
                .setId(cursor.getLong(columns.getIdColumn()))
                .setQuestionId(cursor.getString(columns.getQuestionIdColumn()))
                .setFilename(cursor.getString(columns.getFilenameColumn()))
                .setIncludeFlag(cursor.getInt(columns.getIncludeColumn()) == 1)
                .setIteration(cursor.getInt(columns.getIterationColumn()));
    }

    /**
     *  Adapt(clone) responses for the current survey instance:
     *  Get rid of its Id and update the SurveyInstance Id
     */
    public Map<String, QuestionResponse> getResponsesForPreFilledSurvey(long surveyInstanceId,
            long newSurveyInstanceId) {
        Map<String, QuestionResponse> responses = new HashMap<>();

        Cursor cursor = surveyDbAdapter.getResponses(surveyInstanceId);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                QuestionResponseColumns columns = new QuestionResponseColumns(cursor);
                do {
                    QuestionResponse response = getQuestionResponseBuilder(cursor, columns)
                            .setId(null)
                            .setSurveyInstanceId(newSurveyInstanceId)
                            .createQuestionResponse();
                    responses.put(response.getResponseKey(), response);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return responses;
    }

    public QuestionResponse getResponse(Long surveyInstanceId, String questionId) {
        QuestionResponse resp = null;
        Cursor cursor = surveyDbAdapter.getResponse(surveyInstanceId, questionId);
        if (cursor != null && cursor.moveToFirst()) {
            QuestionResponseColumns columns = new QuestionResponseColumns(cursor);
            resp = getQuestionResponseBuilder(cursor, columns)
                    .setQuestionId(questionId)
                    .setSurveyInstanceId(surveyInstanceId)
                    .createQuestionResponse();
        }

        if (cursor != null) {
            cursor.close();
        }

        return resp;
    }

    @NonNull
    private QuestionResponse getResponseToSave(@NonNull QuestionResponse newResponse) {
        QuestionResponse resp;
        Long surveyInstanceId = newResponse.getSurveyInstanceId();
        String questionId = newResponse.getQuestionId();

        Cursor cursor;
        if (newResponse.isAnswerToRepeatableGroup()) {
            cursor = surveyDbAdapter
                    .getResponse(surveyInstanceId, questionId, newResponse.getIteration());
        } else {
            cursor = surveyDbAdapter.getResponse(surveyInstanceId, questionId);
        }
        if (cursor != null && cursor.moveToFirst()) {
            String type = cursor.getString(cursor.getColumnIndexOrThrow(ResponseColumns.TYPE));
            if (newResponse.getType() != null) {
                type = newResponse.getType();
            }
            Long id = cursor.getLong(cursor.getColumnIndexOrThrow(ResponseColumns._ID));
            int iteration = cursor.getInt(cursor.getColumnIndexOrThrow(ResponseColumns.ITERATION));
            resp = new QuestionResponse.QuestionResponseBuilder()
                    .setValue(newResponse.getValue())
                    .setType(type)
                    .setId(id)
                    .setSurveyInstanceId(surveyInstanceId)
                    .setQuestionId(questionId)
                    .setFilename(newResponse.getFilename())
                    .setIncludeFlag(newResponse.getIncludeFlag())
                    .setIteration(iteration)
                    .createQuestionResponse();
        } else {
            resp = new QuestionResponse.QuestionResponseBuilder()
                    .createFromQuestionResponse(newResponse);
        }

        if (cursor != null) {
            cursor.close();
        }
        return resp;
    }


    /**
     * inserts or updates a question response after first looking to see if it
     * already exists in the database.
     *
     * @param newResponse new QuestionResponseData to insert
     * @return
     */
    public QuestionResponse createOrUpdateSurveyResponse(@NonNull QuestionResponse newResponse) {
        QuestionResponse responseToSave = getResponseToSave(newResponse);
        ContentValues initialValues = new ContentValues();
        initialValues.put(ResponseColumns.ANSWER, responseToSave.getValue());
        initialValues.put(ResponseColumns.TYPE, responseToSave.getType());
        initialValues.put(ResponseColumns.QUESTION_ID, responseToSave.getQuestionId());
        initialValues.put(ResponseColumns.SURVEY_INSTANCE_ID, responseToSave.getSurveyInstanceId());
        initialValues.put(ResponseColumns.FILENAME, responseToSave.getFilename());
        initialValues.put(ResponseColumns.INCLUDE, responseToSave.getIncludeFlag() ? 1 : 0);
        initialValues.put(ResponseColumns.ITERATION, responseToSave.getIteration());
        long id = surveyDbAdapter.updateSurveyResponse(responseToSave.getId(), initialValues);
        return new QuestionResponse.QuestionResponseBuilder().createFromQuestionResponse(
                responseToSave, id);
    }

    public long createSurveyRespondent(String surveyId, double version, User user,
            String surveyedLocaleId) {
        final long time = System.currentTimeMillis();

        ContentValues initialValues = new ContentValues();
        initialValues.put(SurveyInstanceColumns.SURVEY_ID, surveyId);
        initialValues.put(SurveyInstanceColumns.VERSION, version);
        initialValues.put(SurveyInstanceColumns.USER_ID, user.getId());
        initialValues.put(SurveyInstanceColumns.STATUS, SurveyInstanceStatus.SAVED);
        initialValues.put(SurveyInstanceColumns.UUID, PlatformUtil.uuid());
        initialValues.put(SurveyInstanceColumns.START_DATE, time);
        initialValues.put(SurveyInstanceColumns.SAVED_DATE, time);// Default to START_TIME
        initialValues.put(SurveyInstanceColumns.RECORD_ID, surveyedLocaleId);
        // Make submitter field available before submission
        initialValues.put(SurveyInstanceColumns.SUBMITTER, user.getName());
        return surveyDbAdapter.createSurveyRespondent(initialValues);
    }

    /**
     * returns a list of survey objects that are out of date (missing from the
     * db or with a lower version number). If a survey is present but marked as
     * deleted, it will not be listed as out of date (and thus won't be updated)
     *
     * @param surveys
     * @return
     */
    public List<Survey> fetchOutDatedSurveys(List<Survey> surveys) {
        List<Survey> outOfDateSurveys = new ArrayList<>();
        for (int i = 0; i < surveys.size(); i++) {
            Survey survey = surveys.get(i);
            Cursor cursor = briteSurveyDbAdapter.getSurveys(survey.getId(),
                    survey.getVersion() + "");

            if (cursor == null || cursor.getCount() <= 0) {
                outOfDateSurveys.add(survey);
            }
            if (cursor != null) {
                cursor.close();
            }
        }
        return outOfDateSurveys;
    }

    /**
     * updates a survey in the db and resets the deleted flag to "N"
     *
     * @param survey
     * @return
     */
    public void saveSurvey(Survey survey) {

        final long surveyGroupId = survey.getSurveyGroup() != null ?
                survey.getSurveyGroup().getId()
                : SurveyGroup.ID_NONE;

        ContentValues updatedValues = new ContentValues();
        updatedValues.put(SurveyColumns.SURVEY_ID, survey.getId());
        updatedValues.put(SurveyColumns.VERSION, survey.getVersion());
        updatedValues.put(SurveyColumns.TYPE, survey.getType());
        updatedValues.put(SurveyColumns.LOCATION, survey.getLocation());
        updatedValues.put(SurveyColumns.FILENAME, survey.getFileName());
        updatedValues.put(SurveyColumns.NAME, survey.getName());
        updatedValues.put(SurveyColumns.LANGUAGE, survey.getDefaultLanguageCode());
        updatedValues.put(SurveyColumns.SURVEY_GROUP_ID, surveyGroupId);
        updatedValues.put(SurveyColumns.HELP_DOWNLOADED, survey.isHelpDownloaded() ? 1 : 0);

        briteSurveyDbAdapter.updateSurvey(updatedValues, survey.getId());
    }

    /**
     * Gets a single survey from the db using its survey id
     */
    public Survey getSurvey(String surveyId) {
        Survey survey = null;
        Cursor cursor = surveyDbAdapter.getSurvey(surveyId);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                survey = surveyMapper.getSurvey(cursor);
            }
            cursor.close();
        }

        return survey;
    }

    @NonNull
    private List<FileTransmission> getFileTransmissions(Cursor cursor) {
        List<FileTransmission> transmissions = new ArrayList<>();

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                final int startCol = cursor.getColumnIndexOrThrow(TransmissionColumns.START_DATE);
                final int endCol = cursor.getColumnIndexOrThrow(TransmissionColumns.END_DATE);
                final int idCol = cursor.getColumnIndexOrThrow(TransmissionColumns._ID);
                final int formIdCol = cursor.getColumnIndexOrThrow(TransmissionColumns.SURVEY_ID);
                final int surveyInstanceCol = cursor
                        .getColumnIndexOrThrow(TransmissionColumns.SURVEY_INSTANCE_ID);
                final int fileCol = cursor.getColumnIndexOrThrow(TransmissionColumns.FILENAME);
                final int statusCol = cursor.getColumnIndexOrThrow(TransmissionColumns.STATUS);

                transmissions = new ArrayList<>();
                do {
                    FileTransmission trans = new FileTransmission();
                    trans.setId(cursor.getLong(idCol));
                    trans.setFormId(cursor.getString(formIdCol));
                    trans.setRespondentId(cursor.getLong(surveyInstanceCol));
                    trans.setFileName(cursor.getString(fileCol));
                    trans.setStatus(cursor.getInt(statusCol));

                    // Start and End date. Handle null cases
                    if (!cursor.isNull(startCol)) {
                        trans.setStartDate(new Date(cursor.getLong(startCol)));
                    }
                    if (!cursor.isNull(endCol)) {
                        trans.setEndDate(new Date(cursor.getLong(endCol)));
                    }

                    transmissions.add(trans);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        return transmissions;
    }

    public List<FileTransmission> getFileTransmissions(long surveyInstanceId) {
        Cursor cursor = surveyDbAdapter.getFileTransmissions(surveyInstanceId);
        return getFileTransmissions(cursor);
    }

    /**
     * Get the list of queued and failed transmissions
     */
    @NonNull
    public List<FileTransmission> getUnSyncedTransmissions() {
        Cursor cursor = surveyDbAdapter.getUnSyncedTransmissions(new String[] {
                String.valueOf(TransmissionStatus.FAILED),
                String.valueOf(TransmissionStatus.IN_PROGRESS),// Stalled IN_PROGRESS files
                String.valueOf(TransmissionStatus.QUEUED)
        });
        return getFileTransmissions(cursor);
    }

    public void addSurveyGroup(SurveyGroup surveyGroup) {
        ContentValues values = new ContentValues();
        values.put(SurveyGroupColumns.SURVEY_GROUP_ID, surveyGroup.getId());
        values.put(SurveyGroupColumns.NAME, surveyGroup.getName());
        values.put(SurveyGroupColumns.REGISTER_SURVEY_ID, surveyGroup.getRegisterSurveyId());
        values.put(SurveyGroupColumns.MONITORED, surveyGroup.isMonitored() ? 1 : 0);
        briteSurveyDbAdapter.addSurveyGroup(values);
    }

    // Attempt to fetch the registration form. If the form ID is explicitely set on the SurveyGroup,
    // we simply query by ID. Otherwise, assume is a non-monitored form, and query the first form
    // we find.
    public Survey getRegistrationForm(SurveyGroup sg) {
        String formId = sg.getRegisterSurveyId();
        if (!TextUtils.isEmpty(formId) && !"null".equalsIgnoreCase(formId)) {
            return getSurvey(formId);
        }
        Survey s = null;
        Cursor c = briteSurveyDbAdapter.getSurveys(sg.getId());
        if (c != null) {
            if (c.moveToFirst()) {
                s = surveyMapper.getSurvey(c);
            }
            c.close();
        }
        return s;
    }

    private SurveyGroup getSurveyGroup(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(SurveyGroupColumns.SURVEY_GROUP_ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(SurveyGroupColumns.NAME));
        String registerSurveyId = cursor
                .getString(cursor.getColumnIndexOrThrow(SurveyGroupColumns.REGISTER_SURVEY_ID));
        boolean monitored =
                cursor.getInt(cursor.getColumnIndexOrThrow(SurveyGroupColumns.MONITORED)) > 0;
        return new SurveyGroup(id, name, registerSurveyId, monitored);
    }

    public SurveyGroup getSurveyGroup(long id) {
        SurveyGroup sg = null;
        Cursor c = surveyDbAdapter.getSurveyGroup(id);
        if (c != null && c.moveToFirst()) {
            sg = getSurveyGroup(c);
            c.close();
        }

        return sg;
    }

    /**
     * Convenience method to retrieve all non-deleted surveys, without the hassle of
     * parsing the Cursor columns.
     * To get the Cursor result, use getSurveys(surveyGroupId)
     */
    public List<Survey> getSurveyList(long surveyGroupId) {
        // Reuse getSurveys() method
        Cursor cursor = briteSurveyDbAdapter.getSurveys(surveyGroupId);

        ArrayList<Survey> surveys = new ArrayList<>();

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                do {
                    surveys.add(surveyMapper.getSurvey(cursor));
                } while (cursor.moveToNext());
            }
            cursor.close();
        }
        return surveys;
    }

    public void updateSurveyedLocale(long surveyInstanceId, String response,
            SurveyDbAdapter.SurveyedLocaleMeta surveyedLocaleMeta) {
        if (!TextUtils.isEmpty(response)) {
            String surveyedLocaleId = surveyDbAdapter.getSurveyedLocaleId(surveyInstanceId);
            ContentValues surveyedLocaleValues = new ContentValues();

            String questionId = null;
            String type = null;

            switch (surveyedLocaleMeta) {
                case NAME:
                    surveyedLocaleValues.put(RecordColumns.NAME, response);
                    type = "META_NAME";
                    questionId = ConstantUtil.QUESTION_LOCALE_NAME;
                    break;
                case GEOLOCATION:
                    String[] parts = response.split("\\|");
                    if (parts.length < 2) {
                        return;// Wrong format
                    }
                    surveyedLocaleValues.put(RecordColumns.LATITUDE, Double.parseDouble(parts[0]));
                    surveyedLocaleValues.put(RecordColumns.LONGITUDE, Double.parseDouble(parts[1]));
                    type = "META_GEO";
                    questionId = ConstantUtil.QUESTION_LOCALE_GEO;
                    break;
                default:
                    break;
            }
            QuestionResponse metaResponse = new QuestionResponse.QuestionResponseBuilder()
                    .setValue(response)
                    .setType(type)
                    .setSurveyInstanceId(surveyInstanceId)
                    .setQuestionId(questionId)
                    .setIncludeFlag(true)
                    .createQuestionResponse();
            // Update the surveyed locale info
            briteSurveyDbAdapter.updateDataPoint(surveyedLocaleId, surveyedLocaleValues);

            // Store the META_NAME/META_GEO as a response
            createOrUpdateSurveyResponse(metaResponse);
        }
    }

    public void markSurveyHelpDownloaded(String sid, boolean b) {
        briteSurveyDbAdapter.markSurveyHelpDownloaded(sid, b);
    }

    public String[] getSurveyIds() {
        return briteSurveyDbAdapter.getSurveyIds();
    }

    public void deleteAllSurveys() {
        briteSurveyDbAdapter.deleteAllSurveys();
    }

    public void reinstallTestSurvey() {
        briteSurveyDbAdapter.reinstallTestSurvey();
    }

    public void deleteEmptyRecords() {
        surveyDbAdapter.deleteEmptyRecords();
    }

    public void deleteEmptySurveyInstances() {
        surveyDbAdapter.deleteEmptySurveyInstances();
    }

    public Cursor getFormInstances(String surveyedLocaleId) {
        return surveyDbAdapter.getFormInstances(surveyedLocaleId);
    }

    public long[] getFormInstances(String recordId, String surveyId, int saved) {
        return surveyDbAdapter.getFormInstances(recordId, surveyId, saved);
    }

    public Long getLastSurveyInstance(String mRecordId, String id) {
        return surveyDbAdapter.getLastSurveyInstance(mRecordId, id);
    }

    public Cursor getFormInstance(long mSurveyInstanceId) {
        return surveyDbAdapter.getFormInstance(mSurveyInstanceId);
    }

    public void updateSurveyStatus(long mSurveyInstanceId, int saved) {
        surveyDbAdapter.updateSurveyStatus(mSurveyInstanceId, saved);
    }

    public void updateRecordModifiedDate(String recordId, long timestamp) {
        briteSurveyDbAdapter.updateRecordModifiedDate(recordId, timestamp);
    }

    public void deleteResponses(String surveyId) {
        surveyDbAdapter.deleteResponses(surveyId);
    }

    public void addSurveyDuration(long mSurveyInstanceId, long timestamp) {
        surveyDbAdapter.addSurveyDuration(mSurveyInstanceId, timestamp);
    }

    public void deleteResponse(long mSurveyInstanceId, String questionId) {
        surveyDbAdapter.deleteResponse(mSurveyInstanceId, questionId);
    }

    private void createTransmission(long surveyInstanceId, String formId, String filename,
            int status) {
        ContentValues values = new ContentValues();
        values.put(TransmissionColumns.SURVEY_INSTANCE_ID, surveyInstanceId);
        values.put(TransmissionColumns.SURVEY_ID, formId);
        values.put(TransmissionColumns.FILENAME, filename);
        values.put(TransmissionColumns.STATUS, status);
        if (TransmissionStatus.SYNCED == status) {
            final String date = String.valueOf(System.currentTimeMillis());
            values.put(TransmissionColumns.START_DATE, date);
            values.put(TransmissionColumns.END_DATE, date);
        }
        surveyDbAdapter.createTransmission(values);
    }

    public void setFileTransmissionFailed(String filename) {
        int rows = updateTransmissionHistory(filename, TransmissionStatus.FAILED);
        if (rows == 0) {
            // Use a dummy "-1" as survey_instance_id, as the database needs that attribute
            createTransmission(-1, null, filename, TransmissionStatus.FAILED);
        }
    }

    public int updateTransmissionHistory(String filename, int status) {
        ContentValues values = new ContentValues();
        values.put(TransmissionColumns.STATUS, status);
        if (TransmissionStatus.SYNCED == status) {
            values.put(TransmissionColumns.END_DATE, System.currentTimeMillis() + "");
        } else if (TransmissionStatus.IN_PROGRESS == status) {
            values.put(TransmissionColumns.START_DATE, System.currentTimeMillis() + "");
        }
        return surveyDbAdapter.updateTransmission(filename, values);
    }

    public void deleteResponse(long mSurveyInstanceId, String questionId, String iteration) {
        surveyDbAdapter.deleteResponse(mSurveyInstanceId, questionId, iteration);
    }

    public void createTransmission(long id, String formId, String filename) {
        surveyDbAdapter.createTransmission(id, formId, filename);
    }

    public Cursor getResponsesData(long surveyInstanceId) {
        return surveyDbAdapter.getResponsesData(surveyInstanceId);
    }

    public Cursor getSurveyInstancesByStatus(int status) {
        return surveyDbAdapter.getSurveyInstancesByStatus(status);
    }

    public void deleteSurvey(String id) {
        briteSurveyDbAdapter.deleteSurvey(id);
    }

    public String createSurveyedLocale(long id) {
        return surveyDbAdapter.createSurveyedLocale(id, PlatformUtil.recordUuid());
    }

    public void clearSurveyedLocaleName(long surveyInstanceId) {
        surveyDbAdapter.clearSurveyedLocaleName(surveyInstanceId);
    }

    public void clearCollectedData() {
        surveyDbAdapter.clearCollectedData();
    }

    public void clearAllData() {
        surveyDbAdapter.clearAllData();
    }

    public long createOrUpdateUser(Long id, String username) {
        return briteSurveyDbAdapter.createOrUpdateUser(id, username);
    }

    public void deleteAllResponses() {
        surveyDbAdapter.deleteAllResponses();
    }
}
