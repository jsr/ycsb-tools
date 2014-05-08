/**
 * MongoDB client binding for YCSB.
 *
 * Submitted by Yen Pai on 5/11/2010.
 *
 * https://gist.github.com/000a66b8db2caf42467b#file_mongo_db.java
 *
 */

package com.yahoo.ycsb.db;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.BasicDBObject;
import com.mongodb.DBAddress;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoOptions;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;

/**
 * MongoDB client for YCSB framework.
 *
 * Properties to set:
 *
 * mongodb.url=mongodb://localhost:27017 mongodb.database=ycsb
 * mongodb.writeConcern=acknowledged
 *
 * @author ypai
 */
public class MongoDbClient extends DB {

    /** Used to include a field in a response. */
    protected static final Integer INCLUDE = Integer.valueOf(1);

    /** A singleton Mongo instance. */
    private static Mongo[] mongos;

    /** The default write concern for the test. */
    private static WriteConcern writeConcern;

    /** The default read preference for the test */
    private static ReadPreference readPreference;

    /** The database to access. */
    private static String database;

    /** Count the number of times initialized to teardown on the last {@link #cleanup()}. */
    private static final AtomicInteger initCount = new AtomicInteger(0);

    private static Random random = new Random();

    private static String[] clients = null;

    /**
     * Initialize any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    @Override
    public void init() throws DBException {
        initCount.incrementAndGet();
        synchronized (INCLUDE) {
            if (mongos != null) {
                return;
            }

            // initialize MongoDb driver
            Properties props = getProperties();
            String urls = props.getProperty("mongodb.url",
                    "mongodb://localhost:27017");

            clients = urls.split(",");
            mongos = new Mongo[clients.length];

            database = props.getProperty("mongodb.database", "ycsb");

            // Set connectionpool to size of ycsb thread pool
            final String maxConnections = props.getProperty("threadcount", "100");

            // write concern
            String writeConcernType = props.getProperty("mongodb.writeConcern", "acknowledged").toLowerCase();
            if ("errors_ignored".equals(writeConcernType)) {
                writeConcern = WriteConcern.ERRORS_IGNORED;
            }
            else if ("unacknowledged".equals(writeConcernType)) {
                writeConcern = WriteConcern.UNACKNOWLEDGED;
            }
            else if ("acknowledged".equals(writeConcernType)) {
                writeConcern = WriteConcern.ACKNOWLEDGED;
            }
            else if ("journaled".equals(writeConcernType)) {
                writeConcern = WriteConcern.JOURNALED;
            }
            else if ("replica_acknowledged".equals(writeConcernType)) {
                writeConcern = WriteConcern.REPLICA_ACKNOWLEDGED;
            }
            else {
                System.err.println("ERROR: Invalid writeConcern: '"
                                + writeConcernType
                                + "'. "
                                + "Must be [ errors_ignored | unacknowledged | acknowledged | journaled | replica_acknowledged ]");
                System.exit(1);
            }

            // readPreference
            String readPreferenceType = props.getProperty("mongodb.readPreference", "primary").toLowerCase();
            if ("primary".equals(readPreferenceType)) {
                readPreference = ReadPreference.primary();
            }
            else if ("primary_preferred".equals(readPreferenceType)) {
                readPreference = ReadPreference.primaryPreferred();
            }
            else if ("secondary".equals(readPreferenceType)) {
                readPreference = ReadPreference.secondary();
            }
            else if ("secondary_preferred".equals(readPreferenceType)) {
                readPreference = ReadPreference.secondaryPreferred();
            }
            else if ("nearest".equals(readPreferenceType)) {
                readPreference = ReadPreference.nearest();
            }
            else {
                System.err.println("ERROR: Invalid readPreference: '"
                                + readPreferenceType
                                + "'. Must be [ primary | primary_preferred | secondary | secondary_preferred | nearest ]");
                System.exit(1);
            }

        	for( int i=0; i< mongos.length; i++) {
                try {
                    // strip out prefix since Java driver doesn't currently support
                    // standard connection format URL yet
                    // http://www.mongodb.org/display/DOCS/Connections

	            	String url = clients[i];

	                if (url.startsWith("mongodb://")) {
	                    url = url.substring(10);
	                }

	                // need to append db to url.
	                url += "/" + database;
	                MongoOptions options = new MongoOptions();
                    options.setCursorFinalizerEnabled(false);
	                options.connectionsPerHost = Integer.parseInt(maxConnections);
	                mongos[i] = new Mongo(new DBAddress(url), options);

	                System.out.println("mongo connection created with " + url);
	        	} catch (Exception e1) {
                    System.err
                            .println("Could not initialize MongoDB connection pool for Loader: "
                                    + e1.toString());
                    e1.printStackTrace();
                    return;
                }
            }
        }
    }

    /**
     * Cleanup any state for this DB.
     * Called once per DB instance; there is one DB instance per client thread.
     */
    @Override
    public void cleanup() throws DBException {
        if (initCount.decrementAndGet() <= 0) {
        	for(int i=0; i<mongos.length; i++) {
            	try {
                    mongos[i].close();
                }
                catch (Exception e1) {
                    System.err.println("Could not close MongoDB connection pool: "
                            + e1.toString());
                    e1.printStackTrace();
                    return;
                }
        	}
        }
    }

    /**
     * Delete a record from the database.
     *
     * @param table The name of the table
     * @param key The record key of the record to delete.
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    @Override
    public int delete(String table, String key) {
        com.mongodb.DB db = null;
        try {
        	db = mongos[random.nextInt(mongos.length)].getDB(database);
            db.requestStart();
            DBCollection collection = db.getCollection(table);
            DBObject q = new BasicDBObject().append("_id", key);
            WriteResult res = collection.remove(q, writeConcern);
            return 0;
        }
        catch (Exception e) {
            System.err.println(e.toString());
            return 1;
        }
        finally {
            if (db != null) {
                db.requestDone();
            }
        }
    }

    /**
     * Insert a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key.
     *
     * @param table The name of the table
     * @param key The record key of the record to insert.
     * @param values A HashMap of field/value pairs to insert in the record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    @Override
    public int insert(String table, String key,
            HashMap<String, ByteIterator> values) {
        com.mongodb.DB db = null;
        try {
            db = mongos[random.nextInt(mongos.length)].getDB(database);

            db.requestStart();

            DBCollection collection = db.getCollection(table);
            DBObject r = new BasicDBObject().append("_id", key);
            for (String k : values.keySet()) {
                r.put(k, values.get(k).toArray());
            }
            WriteResult res = collection.insert(r, writeConcern);
            return 0;
        }
        catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        finally {
            if (db != null) {
                db.requestDone();
            }
        }
    }

    /**
     * Read a record from the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param key The record key of the record to read.
     * @param fields The list of fields to read, or null for all of them
     * @param result A HashMap of field/value pairs for the result
     * @return Zero on success, a non-zero error code on error or "not found".
     */
    @Override
    @SuppressWarnings("unchecked")
    public int read(String table, String key, Set<String> fields,
            HashMap<String, ByteIterator> result) {
        com.mongodb.DB db = null;
        try {
            db = mongos[random.nextInt(mongos.length)].getDB(database);

            db.requestStart();

            DBCollection collection = db.getCollection(table);
            DBObject q = new BasicDBObject().append("_id", key);
            DBObject fieldsToReturn = new BasicDBObject();

            DBObject queryResult = null;
            if (fields != null) {
                Iterator<String> iter = fields.iterator();
                while (iter.hasNext()) {
                    fieldsToReturn.put(iter.next(), INCLUDE);
                }
                queryResult = collection.findOne(q, fieldsToReturn, readPreference);
            }
            else {
                queryResult = collection.findOne(q, null, readPreference);
            }

            if (queryResult != null) {
                result.putAll(queryResult.toMap());
            }
            return queryResult != null ? 0 : 1;
        }
        catch (Exception e) {
            System.err.println(e.toString());
            return 1;
        }
        finally {
            if (db != null) {
                db.requestDone();
            }
        }
    }

    /**
     * Update a record in the database. Any field/value pairs in the specified values HashMap will be written into the record with the specified
     * record key, overwriting any existing values with the same field name.
     *
     * @param table The name of the table
     * @param key The record key of the record to write.
     * @param values A HashMap of field/value pairs to update in the record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    @Override
    public int update(String table, String key,
            HashMap<String, ByteIterator> values) {
        com.mongodb.DB db = null;
        try {
            db = mongos[random.nextInt(mongos.length)].getDB(database);

            db.requestStart();

            DBCollection collection = db.getCollection(table);
            DBObject q = new BasicDBObject().append("_id", key);
            DBObject u = new BasicDBObject();
            DBObject fieldsToSet = new BasicDBObject();
            Iterator<String> keys = values.keySet().iterator();
            while (keys.hasNext()) {
                String tmpKey = keys.next();
                fieldsToSet.put(tmpKey, values.get(tmpKey).toArray());

            }
            u.put("$set", fieldsToSet);
            WriteResult res = collection.update(q, u, false, false,
                    writeConcern);
            return 0;
        }
        catch (Exception e) {
            System.err.println(e.toString());
            return 1;
        }
        finally {
            if (db != null) {
                db.requestDone();
            }
        }
    }

    /**
     * Perform a range scan for a set of records in the database. Each field/value pair from the result will be stored in a HashMap.
     *
     * @param table The name of the table
     * @param startkey The record key of the first record to read.
     * @param recordcount The number of records to read
     * @param fields The list of fields to read, or null for all of them
     * @param result A Vector of HashMaps, where each HashMap is a set field/value pairs for one record
     * @return Zero on success, a non-zero error code on error. See this class's description for a discussion of error codes.
     */
    @Override
    public int scan(String table, String startkey, int recordcount,
            Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
        com.mongodb.DB db = null;
        DBCursor cursor = null;
        try {
            db = mongos[random.nextInt(mongos.length)].getDB(database);
            db.requestStart();
            DBCollection collection = db.getCollection(table);
            // { "_id":{"$gte":startKey, "$lte":{"appId":key+"\uFFFF"}} }
            DBObject scanRange = new BasicDBObject().append("$gte", startkey);
            DBObject q = new BasicDBObject().append("_id", scanRange);
            cursor = collection.find(q).limit(recordcount);
            while (cursor.hasNext()) {
                // toMap() returns a Map, but result.add() expects a
                // Map<String,String>. Hence, the suppress warnings.
                HashMap<String, ByteIterator> resultMap = new HashMap<String, ByteIterator>();

                DBObject obj = cursor.next();
                fillMap(resultMap, obj);

                result.add(resultMap);
            }

            return 0;
        }
        catch (Exception e) {
            System.err.println(e.toString());
            return 1;
        }
        finally {
            if (db != null) {
                if( cursor != null ) {
                    cursor.close();
                }
                db.requestDone();
            }
        }

    }

    /**
     * TODO - Finish
     *
     * @param resultMap
     * @param obj
     */
    @SuppressWarnings("unchecked")
    protected void fillMap(HashMap<String, ByteIterator> resultMap, DBObject obj) {
        Map<String, Object> objMap = obj.toMap();
        for (Map.Entry<String, Object> entry : objMap.entrySet()) {
            if (entry.getValue() instanceof byte[]) {
                resultMap.put(entry.getKey(), new ByteArrayByteIterator(
                        (byte[]) entry.getValue()));
            }
        }
    }
}
