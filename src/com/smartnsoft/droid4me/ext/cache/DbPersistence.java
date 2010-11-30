/* 
 * Copyright (C) 2008-2009 E2M.
 *
 * The code hereby is the private full property of the E2M company, Paris, France.
 * 
 * You have no right to re-use or modify it. There are no open-source, nor free licence
 * attached to it!
 */

package com.smartnsoft.droid4me.ext.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

import com.smartnsoft.droid4me.bo.Business;
import com.smartnsoft.droid4me.cache.Persistence;

/**
 * Enables to store some input streams on a SQLite database.
 * 
 * @author �douard Mercier
 * @since 2009.06.19
 */
// TODO: think on how implementing the URI usage
// TODO: think on how to limit the database size
public final class DbPersistence
    extends Persistence
{

  private final static class CacheColumns
      implements BaseColumns
  {

    public static final String URI = "uri";

    public static final String CONTENTS = "contents";

    public static final String LAST_UPDATE = "lastUpdate";

    private CacheColumns()
    {
    }

  }

  public final static String DEFAULT_FILE_NAME = "cache.db";

  public final static String DEFAULT_TABLE_NAME = "cache";

  public static String[] FILE_NAMES = new String[] { DbPersistence.DEFAULT_FILE_NAME };

  public static String[] TABLE_NAMES = new String[] { DbPersistence.DEFAULT_TABLE_NAME };

  private static int threadCount = 1;

  /**
   * The number of simultaneous available threads in the pool.
   */
  private static final int THREAD_POOL_DEFAULT_SIZE = 3;

  private final static ThreadPoolExecutor THREAD_POOL = new ThreadPoolExecutor(1, DbPersistence.THREAD_POOL_DEFAULT_SIZE, 5l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory()
  {
    public Thread newThread(Runnable runnable)
    {
      final Thread thread = new Thread(runnable);
      thread.setName("droid4me.ext-dbpersistence-thread #" + DbPersistence.threadCount++);
      return thread;
    }
  });

  /**
   * In order to put in common the databases connections.
   */
  private static Map<String, SQLiteDatabase> writeableDbs = new HashMap<String, SQLiteDatabase>();

  private final String fileName;

  private final String tableName;

  private SQLiteDatabase writeableDb;

  /**
   * Defined in order to make the {@link #readInputStream(String)} method more optimized when computing its underlying SQL query.
   */
  private final String readInputStreamQuery;

  /**
   * Defined in order to make the {@link #writeInputStream()} method more optimized when determining whether to insert or update.
   */
  private SQLiteStatement writeInputStreamExistsStatement;

  private final Object writeInputStatementSyncObject = new Object();

  /**
   * Defined in order to make the {@link #getLastUpdate(String)} method more optimized.
   */
  private SQLiteStatement getLastUpdateStreamExistsStatement;

  private final Object getLastUpdateStatementSyncObject = new Object();

  public DbPersistence(String storageDirectoryPath, int instanceIndex)
  {
    super(storageDirectoryPath, instanceIndex);
    this.fileName = DbPersistence.FILE_NAMES[instanceIndex];
    this.tableName = DbPersistence.TABLE_NAMES[instanceIndex];
    readInputStreamQuery = new StringBuilder("SELECT ").append(DbPersistence.CacheColumns.CONTENTS).append(", ").append(DbPersistence.CacheColumns.LAST_UPDATE).append(
        " FROM ").append(tableName).append(" WHERE ").append(DbPersistence.CacheColumns.URI).append(" = ?").toString();
  }

  // TODO: think of minimizing this start-up process
  @Override
  protected synchronized void initialize()
  {
    final String dbFilePath = getStorageDirectoryPath() + "/" + fileName;
    try
    {
      DbPersistence.ensureDatabaseAvailability(dbFilePath, tableName);
    }
    catch (SQLiteException exception)
    {
      if (log.isInfoEnabled())
      {
        log.info("The cache database seems to be unexisting, unavailable or corrupted: it is now re-initialized");
      }
      try
      {
        final File databaseFile = new File(dbFilePath);
        databaseFile.delete();
        databaseFile.getParentFile().mkdirs();
        DbPersistence.ensureDatabaseAvailability(dbFilePath, tableName);
      }
      catch (Exception otherException)
      {
        if (log.isErrorEnabled())
        {
          log.error("Cannot properly initialize the cache database: no database caching is available!", otherException);
        }
        return;
      }
    }
    try
    {
      writeableDb = DbPersistence.getDatabase(dbFilePath);
      // Ideally, this compiled statement should be computed here, but when the table is created, it seems that the calling method returns before the
      // work is done.
      // Hence, we perform some lazy instantiation
      // writeInputStreamExistsStatement = writeableDb.compileStatement("SELECT COUNT(1) FROM " + tableName + " WHERE " +
      // DbPersistence.CacheColumns.URI + " = ?");
      storageBackendAvailable = true;
    }
    catch (SQLiteException exception)
    {
      if (log.isErrorEnabled())
      {
        log.error("Cannot properly open the cache database: no database caching is available!", exception);
      }
    }
  }

  /**
   * This enables to share the database instances when possible.
   */
  private static synchronized SQLiteDatabase getDatabase(String filePath)
  {
    SQLiteDatabase database = DbPersistence.writeableDbs.get(filePath);
    if (database == null)
    {
      database = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READWRITE);
      database.setLockingEnabled(true);
      DbPersistence.writeableDbs.put(filePath, database);
    }
    return database;
  }

  private static void ensureDatabaseAvailability(String dbFilePath, String tableName)
  {
    final SQLiteDatabase database = SQLiteDatabase.openDatabase(dbFilePath, null, SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.OPEN_READWRITE);
    database.setLockingEnabled(true);
    try
    {
      final int version = database.getVersion();
      // if (log.isDebugEnabled())
      // {
      // log.debug("The cache database version is '" + version + "'");
      // }
      // Just like a "SELECT * FROM sqlite_master WHERE name = 'whatever' AND type = 'table'"
      final boolean tableExists;
      {
        final Cursor cursor = database.query("sqlite_master", new String[] { "name" }, "name='" + tableName + "' AND type = 'table'", null, null, null, null);
        try
        {
          tableExists = (cursor.moveToFirst() == true);
        }
        finally
        {
          cursor.close();
        }
      }
      final int expectedVersion = 1;
      if (tableExists == false || version != expectedVersion)
      {
        if (log.isInfoEnabled())
        {
          log.info("Creating the table '" + tableName + "' in the database located at '" + dbFilePath + "' because it does not already exist");
        }
        database.beginTransaction();
        try
        {
          database.execSQL("CREATE TABLE " + tableName + " (" + DbPersistence.CacheColumns._ID + " INTEGER PRIMARY KEY, " + DbPersistence.CacheColumns.URI + " TEXT, " + DbPersistence.CacheColumns.LAST_UPDATE + " TIMESTAMP, " + DbPersistence.CacheColumns.CONTENTS + " BLOG);");
          // We create an index, so as to optimize the database performance
          database.execSQL("CREATE UNIQUE INDEX " + tableName + "_index ON " + tableName + " ( " + DbPersistence.CacheColumns.URI + " )");
          database.setVersion(expectedVersion);
          database.setTransactionSuccessful();
        }
        finally
        {
          database.endTransaction();
        }
      }
    }
    finally
    {
      database.close();
    }
  }

  public Date getLastUpdate(String uri)
  {
    if (storageBackendAvailable == false)
    {
      return null;
    }

    synchronized (getLastUpdateStatementSyncObject)
    {
      if (getLastUpdateStreamExistsStatement == null)
      {
        // Lazy instantiation, not totally thread-safe, but a work-around
        getLastUpdateStreamExistsStatement = writeableDb.compileStatement("SELECT " + DbPersistence.CacheColumns.LAST_UPDATE + " FROM " + tableName + " WHERE " + DbPersistence.CacheColumns.URI + " = ?");
      }
      getLastUpdateStreamExistsStatement.bindString(1, uri);
      writeableDb.beginTransaction();
      try
      {
        final long date;
        try
        {
          date = getLastUpdateStreamExistsStatement.simpleQueryForLong();
          writeableDb.setTransactionSuccessful();
        }
        catch (SQLiteDoneException exception)
        {
          writeableDb.setTransactionSuccessful();
          return null;
        }
        return new Date(date);
      }
      finally
      {
        writeableDb.endTransaction();
      }
    }
  }

  public InputStream getRawInputStream(String uri)
      throws Persistence.PersistenceException
  {
    final Business.InputAtom atom = readInputStream(uri);
    return (atom == null ? null : atom.inputStream);
  }

  public Business.InputAtom readInputStream(String uri)
      throws Persistence.PersistenceException
  {
    if (storageBackendAvailable == false)
    {
      return null;
    }
    if (log.isDebugEnabled())
    {
      log.debug("Reading from the table '" + tableName + "' the contents related to the URI '" + uri + "'");
    }

    final long start = System.currentTimeMillis();
    writeableDb.beginTransaction();
    Cursor cursor;
    try
    {
      /*
       * final SQLiteQueryBuilder qb = new SQLiteQueryBuilder(); qb.setTables(tableName); final Cursor cursor = qb.query(writeableDb, null,
       * DbPersistence.CacheColumns.URI + " = '" + uri + "'", null, null, null, null);
       */
      try
      {
        cursor = writeableDb.rawQuery(readInputStreamQuery, new String[] { uri });
        writeableDb.setTransactionSuccessful();
      }
      catch (SQLException exception)
      {
        // Cannot figure out why the first time the database is accessed once it has just been created, an exception is thrown on that query
        // Re-running it, fixes the problem ;(
        // Hence, we silently ignore the previous exception
        cursor = writeableDb.rawQuery(readInputStreamQuery, new String[] { uri });
        writeableDb.setTransactionSuccessful();
      }
    }
    finally
    {
      writeableDb.endTransaction();
    }
    try
    {
      if (cursor.moveToFirst() == false)
      {
        return null;
      }
      final byte[] blob = cursor.getBlob(cursor.getColumnIndex(DbPersistence.CacheColumns.CONTENTS));
      final Date timestamp = new Date(cursor.getLong(cursor.getColumnIndex(DbPersistence.CacheColumns.LAST_UPDATE)));
      final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(blob);
      if (log.isDebugEnabled())
      {
        log.debug("Read from the table '" + tableName + "' the contents related to the URI '" + uri + "' in " + (System.currentTimeMillis() - start) + " ms");
      }
      return new Business.InputAtom(timestamp, byteArrayInputStream);
    }
    finally
    {
      if (cursor != null)
      {
        cursor.close();
      }
    }
  }

  @Override
  public InputStream cacheInputStream(String uri, InputStream inputStream)
      throws Persistence.PersistenceException
  {
    return internalCacheInputStream(uri, inputStream, null, false);
  }

  public InputStream writeInputStream(String uri, Business.InputAtom atom)
      throws Persistence.PersistenceException
  {
    return internalCacheInputStream(uri, atom.inputStream, atom.timestamp, true);
  }

  public void remove(String uri)
  {
    if (log.isDebugEnabled())
    {
      log.debug("Removing from the table '" + tableName + "' the contents related to the URI '" + uri + "'");
    }
    if (storageBackendAvailable == false)
    {
      return;
    }
    writeableDb.beginTransaction();
    try
    {
      writeableDb.delete(tableName, DbPersistence.CacheColumns.URI + " = '" + uri + "'", null);
      writeableDb.setTransactionSuccessful();
    }
    finally
    {
      writeableDb.endTransaction();
    }
  }

  protected void empty()
      throws Persistence.PersistenceException
  {
    writeableDb.beginTransaction();
    try
    {
      // We delete all the table rows
      writeableDb.delete(tableName, null, null);
      writeableDb.setTransactionSuccessful();
    }
    finally
    {
      writeableDb.endTransaction();
    }
  }

  private InputStream internalCacheInputStream(final String uri, InputStream inputStream, final Date timestamp, final boolean asynchronous)
      throws Persistence.PersistenceException
  {
    if (storageBackendAvailable == false)
    {
      return inputStream;
    }

    // We immediately duplicate the input stream
    final long start = System.currentTimeMillis();
    final ByteArrayInputStream newInputStream;
    final byte[] bytes;
    if (inputStream != null)
    {
      final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      try
      {
        final byte buffer[] = new byte[32768];
        int length;
        while ((length = inputStream.read(buffer)) > 0)
        {
          outputStream.write(buffer, 0, length);
        }
        bytes = outputStream.toByteArray();
        newInputStream = new ByteArrayInputStream(bytes);
      }
      catch (IOException exception)
      {
        throw new Persistence.PersistenceException();
      }
      finally
      {
        try
        {
          outputStream.close();
        }
        catch (IOException exception)
        {
          // Does not matter
        }
      }
    }
    else
    {
      bytes = new byte[0];
      newInputStream = null;
    }
    if (log.isDebugEnabled())
    {
      log.debug("Loaded in memory the output stream related to the URI '" + uri + "' " + bytes.length + " bytes in " + (System.currentTimeMillis() - start) + " ms");
    }
    if (asynchronous == false)
    {
      updateDb(uri, timestamp, bytes, start, asynchronous);
    }
    else
    {
      DbPersistence.THREAD_POOL.execute(new Runnable()
      {
        public void run()
        {
          try
          {
            updateDb(uri, timestamp, bytes, start, asynchronous);
          }
          catch (Throwable throwable)
          {
            // TODO: use a listener over SQL exception, so that those problem can be handled properly
            if (log.isErrorEnabled())
            {
              log.error("An error occurred while updating asynchronously the table '" + tableName + "' the contents related to the URI '" + uri, throwable);
            }
          }
        }
      });
    }
    return newInputStream;
  }

  private void updateDb(String uri, Date timestamp, byte[] bytes, long start, boolean asynchronous)
  {
    if (log.isDebugEnabled())
    {
      log.debug("Updating or inserting " + (asynchronous == true ? "asynchronously" : "synchronously") + " the table '" + tableName + "' the contents related to the URI '" + uri + "' with data of " + bytes.length + " bytes");
    }
    // We first determine whether the row should be created or inserted
    /*
     * final SQLiteQueryBuilder qb = new SQLiteQueryBuilder(); qb.setTables(tableName); final Cursor cursor = qb.query(writeableDb, null,
     * DbPersistence.CacheColumns.URI + " = '" + uri + "'", null, null, null, null);
     */
    // Optimization
    final long result;

    writeableDb.beginTransaction();
    try
    {
      // Fixes the "android.database.sqlite.SQLiteMisuseException: library routine called out of sequence" problem!
      synchronized (writeInputStatementSyncObject)
      {
        if (writeInputStreamExistsStatement == null)
        {
          // Lazy instantiation, not totally thread-safe, but a work-around
          writeInputStreamExistsStatement = writeableDb.compileStatement("SELECT COUNT(1) FROM " + tableName + " WHERE " + DbPersistence.CacheColumns.URI + " = ?");
        }
        writeInputStreamExistsStatement.bindString(1, uri);
        result = writeInputStreamExistsStatement.simpleQueryForLong();
      }
      final boolean insert = result == 0;
      /*
       * final Cursor cursor = writeableDb.rawQuery("SELECT COUNT(1) FROM " + tableName + " WHERE " + DbPersistence.CacheColumns.URI + " = '" + uri +
       * "'", null); final boolean insert; try { insert = (cursor.moveToFirst() == false); } finally { cursor.close(); }
       */

      // Now, we know whether this will be an update or an insertion
      final ContentValues contentValues = new ContentValues();
      if (insert == true)
      {
        contentValues.put(DbPersistence.CacheColumns.URI, uri);
      }
      if (timestamp != null)
      {
        contentValues.put(DbPersistence.CacheColumns.LAST_UPDATE, timestamp.getTime());
      }
      contentValues.put(DbPersistence.CacheColumns.CONTENTS, bytes);
      if (insert == true)
      {
        writeableDb.insert(tableName, null, contentValues);
      }
      else
      {
        writeableDb.update(tableName, contentValues, DbPersistence.CacheColumns.URI + " = '" + uri + "'", null);
      }
      writeableDb.setTransactionSuccessful();
    }
    finally
    {
      writeableDb.endTransaction();
    }
    if (log.isDebugEnabled())
    {
      log.debug("Wrote into the table '" + tableName + "' regarding the URI '" + uri + "' in " + (System.currentTimeMillis() - start) + " ms");
    }
  }

}
