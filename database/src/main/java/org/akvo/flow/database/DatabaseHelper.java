/*
 * Copyright (C) 2010-2017 Stichting Akvo (Akvo Foundation)
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

package org.akvo.flow.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import org.akvo.flow.database.migration.MigrationListener;
import org.akvo.flow.database.migration.ResponseMigrationHelper;
import org.akvo.flow.database.upgrade.UpgraderFactory;

import java.util.Map;

import timber.log.Timber;

/**
 * Helper class for creating the database tables and loading reference data
 * It is declared with package scope for VM optimizations
 *
 * @author Christopher Fagiani
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "surveydata";
    public static final int VER_LAUNCH = 78;
    public static final int VER_FORM_SUBMITTER = 79;
    public static final int VER_FORM_DEL_CHECK = 80;
    public static final int VER_FORM_VERSION = 81;
    public static final int VER_CADDISFLY_QN = 82;
    public static final int VER_PREFERENCES_MIGRATE = 83;
    public static final int VER_LANGUAGES_MIGRATE = 84;
    public static final int VER_RESPONSE_ITERATION = 85;
    static final int DATABASE_VERSION = VER_RESPONSE_ITERATION;

    private static SQLiteDatabase database;
    private static final Object LOCK_OBJ = new Object();
    private volatile static int instanceCount = 0;
    private final MigrationListener migrationListener;
    private final LanguageTable languageTable;

    public DatabaseHelper(Context context, LanguageTable languageTable,
            MigrationListener migrationListener) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.languageTable = languageTable;
        this.migrationListener = migrationListener;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.USER + " ("
                + UserColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + UserColumns.NAME + " TEXT NOT NULL,"
                + UserColumns.EMAIL + " TEXT,"
                + UserColumns.DELETED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + Tables.SURVEY + " ("
                + SurveyColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SurveyColumns.SURVEY_ID + " TEXT NOT NULL,"
                + SurveyColumns.SURVEY_GROUP_ID + " INTEGER,"// REFERENCES ...
                + SurveyColumns.NAME + " TEXT NOT NULL,"
                + SurveyColumns.VERSION + " REAL,"
                + SurveyColumns.TYPE + " TEXT,"
                + SurveyColumns.LOCATION + " TEXT,"
                + SurveyColumns.FILENAME + " TEXT,"
                + SurveyColumns.LANGUAGE + " TEXT,"
                + SurveyColumns.HELP_DOWNLOADED + " INTEGER NOT NULL DEFAULT 0,"
                + SurveyColumns.DELETED + " INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE (" + SurveyColumns.SURVEY_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SURVEY_GROUP + " ("
                + SurveyGroupColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SurveyGroupColumns.SURVEY_GROUP_ID + " INTEGER,"
                + SurveyGroupColumns.NAME + " TEXT,"
                + SurveyGroupColumns.REGISTER_SURVEY_ID + " TEXT,"
                + SurveyGroupColumns.MONITORED + " INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE (" + SurveyGroupColumns.SURVEY_GROUP_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SURVEY_INSTANCE + " ("
                + SurveyInstanceColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SurveyInstanceColumns.UUID + " TEXT,"
                + SurveyInstanceColumns.SURVEY_ID + " TEXT NOT NULL,"// REFERENCES ...
                + SurveyInstanceColumns.USER_ID + " INTEGER,"
                + SurveyInstanceColumns.START_DATE + " INTEGER,"
                + SurveyInstanceColumns.SAVED_DATE + " INTEGER,"
                + SurveyInstanceColumns.SUBMITTED_DATE + " INTEGER,"
                + SurveyInstanceColumns.RECORD_ID + " TEXT,"
                + SurveyInstanceColumns.STATUS + " INTEGER,"
                + SurveyInstanceColumns.EXPORTED_DATE + " INTEGER,"
                + SurveyInstanceColumns.SYNC_DATE + " INTEGER,"
                + SurveyInstanceColumns.DURATION + " INTEGER NOT NULL DEFAULT 0,"
                + SurveyInstanceColumns.SUBMITTER + " TEXT,"
                + SurveyInstanceColumns.VERSION + " REAL,"
                + "UNIQUE (" + SurveyInstanceColumns.UUID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.RESPONSE + " ("
                + ResponseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ResponseColumns.SURVEY_INSTANCE_ID + " INTEGER NOT NULL,"// REFERENCES...
                + ResponseColumns.QUESTION_ID + " TEXT NOT NULL,"
                + ResponseColumns.ANSWER + " TEXT NOT NULL,"
                + ResponseColumns.TYPE + " TEXT NOT NULL,"
                + ResponseColumns.INCLUDE + " INTEGER NOT NULL DEFAULT 1,"
                + ResponseColumns.FILENAME + " TEXT,"
                + ResponseColumns.ITERATION + " INTEGER NOT NULL DEFAULT -1)");

        db.execSQL("CREATE TABLE " + Tables.RECORD + " ("
                + RecordColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + RecordColumns.RECORD_ID + " TEXT,"
                + RecordColumns.SURVEY_GROUP_ID + " INTEGER,"// REFERENCES ...
                + RecordColumns.NAME + " TEXT,"// REFERENCES ...
                + RecordColumns.LATITUDE + " REAL,"// REFERENCES ...
                + RecordColumns.LONGITUDE + " REAL,"// REFERENCES ...
                + RecordColumns.LAST_MODIFIED + " INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE (" + RecordColumns.RECORD_ID + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.TRANSMISSION + " ("
                + TransmissionColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TransmissionColumns.SURVEY_INSTANCE_ID + " INTEGER NOT NULL,"
                + TransmissionColumns.SURVEY_ID + " TEXT,"
                + TransmissionColumns.FILENAME + " TEXT,"
                + TransmissionColumns.STATUS + " INTEGER,"
                + TransmissionColumns.START_DATE + " INTEGER,"
                + TransmissionColumns.END_DATE + " INTEGER,"
                + "UNIQUE (" + TransmissionColumns.FILENAME + ") ON CONFLICT REPLACE)");

        db.execSQL("CREATE TABLE " + Tables.SYNC_TIME + " ("
                + SyncTimeColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + SyncTimeColumns.SURVEY_GROUP_ID + " INTEGER,"
                + SyncTimeColumns.TIME + " TEXT,"
                + "UNIQUE (" + SyncTimeColumns.SURVEY_GROUP_ID + ") ON CONFLICT REPLACE)");
        languageTable.onCreate(db);
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Timber.d("Upgrading database from version %d to %d", oldVersion, newVersion);
        new UpgraderFactory().createUpgrader(oldVersion, this, db).upgrade();
    }

    public void upgradeFromPreferences(SQLiteDatabase db) {
        languageTable.onCreate(db);
        migrationListener.migrateLanguages(db);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PREFERENCES);
    }

    public void upgradeFromCaddisfly(SQLiteDatabase db) {
        migrationListener.migratePreferences(db);
    }

    public void upgradeFromFormVersion(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + Tables.RESPONSE
                + " ADD COLUMN " + ResponseColumns.FILENAME + " TEXT");
    }

    public void upgradeFromFormCheck(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + Tables.SURVEY_INSTANCE
                + " ADD COLUMN " + SurveyInstanceColumns.VERSION + " REAL");
    }

    public void upgradeFromFormSubmitter(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + Tables.TRANSMISSION
                + " ADD COLUMN " + TransmissionColumns.SURVEY_ID + " TEXT");
    }

    public void upgradeFromLaunch(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + Tables.SURVEY_INSTANCE
                + " ADD COLUMN " + SurveyInstanceColumns.SUBMITTER + " TEXT");
    }

    /**
     * This is not ideal but due to our setup, using something other than getWritableDatabase
     * produces errors.
     * @return
     */
    @Override
    public SQLiteDatabase getReadableDatabase() {
        return getWritableDatabase();
    }


    public void upgradeFromLanguages(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + Tables.RESPONSE
                + " ADD COLUMN " + ResponseColumns.ITERATION + " INTEGER NOT NULL DEFAULT 0");
        ResponseMigrationHelper responseMigrationHelper = new ResponseMigrationHelper();
        Map<Pair<String, String>, ContentValues> responseMigrationData = responseMigrationHelper
                .obtainResponseMigrationData(db);
        responseMigrationHelper.migrateResponses(responseMigrationData, db);
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        synchronized (LOCK_OBJ) {

            if (database == null || !database.isOpen()) {
                database = super.getWritableDatabase();
                instanceCount = 0;
            }
            instanceCount++;
            return database;
        }
    }

    @Override
    public void close() {
        synchronized (LOCK_OBJ) {
            instanceCount--;
            if (instanceCount <= 0) {
                // close the database held by the helper (if any)
                super.close();
                if (database != null && database.isOpen()) {
                    // we may be holding a different database than the
                    // helper so
                    // close that too if it's still open.
                    database.close();
                }
                database = null;
            }
        }
    }

    private void createIndexes(SQLiteDatabase db) {
        // Included in point updates
        db.execSQL("CREATE INDEX response_idx ON " + Tables.RESPONSE + "("
                + ResponseColumns.SURVEY_INSTANCE_ID + ", " + ResponseColumns.QUESTION_ID
                + ")");
        db.execSQL("CREATE INDEX record_name_idx ON " + Tables.RECORD
                + "(" + RecordColumns.NAME + ")");
        db.execSQL("CREATE INDEX response_status_idx ON " + Tables.SURVEY_INSTANCE
                + "(" + SurveyInstanceColumns.STATUS + ")");
        db.execSQL("CREATE INDEX response_modified_idx ON " + Tables.SURVEY_INSTANCE
                + "(" + SurveyInstanceColumns.SUBMITTED_DATE + ")");
    }

    public void dropAllTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + Tables.RESPONSE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SYNC_TIME);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SURVEY);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.PREFERENCES);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.USER);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SURVEY_GROUP);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.SURVEY_INSTANCE);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.RECORD);
        db.execSQL("DROP TABLE IF EXISTS " + Tables.TRANSMISSION);
    }
}
