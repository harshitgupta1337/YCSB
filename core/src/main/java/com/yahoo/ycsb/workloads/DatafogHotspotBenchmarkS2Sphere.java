/**
 * Copyright (c) 2010 Yahoo! Inc., Copyright (c) 2016-2017 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.workloads;

import com.yahoo.ycsb.*;
import com.yahoo.ycsb.generator.*;
import com.yahoo.ycsb.generator.UniformLongGenerator;
import com.yahoo.ycsb.measurements.Measurements;

import com.github.davidmoten.geo.GeoHash;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * The core benchmark scenario. Represents a set of clients doing simple CRUD operations. The
 * relative proportion of different kinds of operations, and other properties of the workload,
 * are controlled by parameters specified at runtime.
 * <p>
 * Properties to control the client:
 * <UL>
 * <LI><b>fieldcount</b>: the number of fields in a record (default: 10)
 * <LI><b>fieldlength</b>: the size of each field (default: 100)
 * <LI><b>readallfields</b>: should reads read all fields (true) or just one (false) (default: true)
 * <LI><b>writeallfields</b>: should updates and read/modify/writes update all fields (true) or just
 * one (false) (default: false)
 * <LI><b>readproportion</b>: what proportion of operations should be reads (default: 0.95)
 * <LI><b>updateproportion</b>: what proportion of operations should be updates (default: 0.05)
 * <LI><b>insertproportion</b>: what proportion of operations should be inserts (default: 0)
 * <LI><b>scanproportion</b>: what proportion of operations should be scans (default: 0)
 * <LI><b>readmodifywriteproportion</b>: what proportion of operations should be read a record,
 * modify it, write it back (default: 0)
 * <LI><b>requestdistribution</b>: what distribution should be used to select the records to operate
 * on - uniform, zipfian, hotspot, sequential, exponential or latest (default: uniform)
 * <LI><b>maxscanlength</b>: for scans, what is the maximum number of records to scan (default: 1000)
 * <LI><b>scanlengthdistribution</b>: for scans, what distribution should be used to choose the
 * number of records to scan, for each scan, between 1 and maxscanlength (default: uniform)
 * <LI><b>insertstart</b>: for parallel loads and runs, defines the starting record for this
 * YCSB instance (default: 0)
 * <LI><b>insertcount</b>: for parallel loads and runs, defines the number of records for this
 * YCSB instance (default: recordcount)
 * <LI><b>zeropadding</b>: for generating a record sequence compatible with string sort order by
 * 0 padding the record number. Controls the number of 0s to use for padding. (default: 1)
 * For example for row 5, with zeropadding=1 you get 'user5' key and with zeropading=8 you get
 * 'user00000005' key. In order to see its impact, zeropadding needs to be bigger than number of
 * digits in the record number.
 * <LI><b>insertorder</b>: should records be inserted in order by key ("ordered"), or in hashed
 * order ("hashed") (default: hashed)
 * </ul>
 */
public class DatafogHotspotBenchmarkS2Sphere extends Workload {
  /**
   * The name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY = "table";

  /**
   * The default name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY_DEFAULT = "usertable";

  protected String table;

  /**
   * The name of the property for the number of fields in a record.
   */
  public static final String FIELD_COUNT_PROPERTY = "fieldcount";

  /**
   * The name of column that stores the partition key
   */
  public static final String LOCATION_HASH_COLUMN_NAME = "s2geometry";

  /**
   * The name of column that stores the timestamp
   */
  public static final String TIMESTAMP_COLUMN_NAME = "timestamp";

  
  public static final String HOTSPOT_MIN_PROPERTY = "hotspot_min";
  public static final String HOTSPOT_MIN_DEFAULT = "";
  public static final String HOTSPOT_MAX_PROPERTY = "hotspot_max";
  public static final String HOTSPOT_MAX_DEFAULT = "";

  public static final String AREA_MIN_PROPERTY = "area_min";
  public static final String AREA_MIN_DEFAULT = "";
  public static final String AREA_MAX_PROPERTY = "area_max";
  public static final String AREA_MAX_DEFAULT = "";

  /**
   * The name of the property for deciding whether to read one field (false) or all fields (true) of
   * a record.
   */
  public static final String READ_ALL_FIELDS_PROPERTY = "readallfields";

  /**
   * The default value for the readallfields property.
   */
  public static final String READ_ALL_FIELDS_PROPERTY_DEFAULT = "true";

  protected boolean readallfields;

  /**
   * The name of the property for deciding whether to write one field (false) or all fields (true)
   * of a record.
   */
  public static final String WRITE_ALL_FIELDS_PROPERTY = "writeallfields";

  /**
   * The default value for the writeallfields property.
   */
  public static final String WRITE_ALL_FIELDS_PROPERTY_DEFAULT = "false";

  protected boolean writeallfields;

  /**
   * The name of the property for deciding whether to check all returned
   * data against the formation template to ensure data integrity.
   */
  public static final String DATA_INTEGRITY_PROPERTY = "dataintegrity";

  /**
   * The default value for the dataintegrity property.
   */
  public static final String DATA_INTEGRITY_PROPERTY_DEFAULT = "false";

  /**
   * Set to true if want to check correctness of reads. Must also
   * be set to true during loading phase to function.
   */
  private boolean dataintegrity;

  /**
   * The name of the property for the proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY = "readproportion";

  /**
   * The default proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY_DEFAULT = "0.95";

  /**
   * The name of the property for the proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY = "updateproportion";

  /**
   * The default proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY_DEFAULT = "0.05";

  /**
   * The name of the property for the proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY = "insertproportion";

  /**
   * The default proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * The name of the property for the proportion of transactions that are scans.
   */
  public static final String SCAN_PROPORTION_PROPERTY = "scanproportion";

  /**
   * The default proportion of transactions that are scans.
   */
  public static final String SCAN_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * The name of the property for the proportion of transactions that are read-modify-write.
   */
  public static final String READMODIFYWRITE_PROPORTION_PROPERTY = "readmodifywriteproportion";

  /**
   * The default proportion of transactions that are scans.
   */
  public static final String READMODIFYWRITE_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * The name of the property for the the distribution of requests across the keyspace. Options are
   * "uniform", "zipfian" and "latest"
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY = "requestdistribution";

  /**
   * The default distribution of requests across the keyspace.
   */
  public static final String REQUEST_DISTRIBUTION_PROPERTY_DEFAULT = "uniform";

  /**
   * The name of the property for adding zero padding to record numbers in order to match
   * string sort order. Controls the number of 0s to left pad with.
   */
  public static final String ZERO_PADDING_PROPERTY = "zeropadding";

  /**
   * The default zero padding value. Matches integer sort order
   */
  public static final String ZERO_PADDING_PROPERTY_DEFAULT = "1";

  /**
   * The name of the property for the max scan length (number of records).
   */
  public static final String MAX_SCAN_LENGTH_PROPERTY = "maxscanlength";

  /**
   * The default max scan length.
   */
  public static final String MAX_SCAN_LENGTH_PROPERTY_DEFAULT = "1000";

  /**
   * The name of the property for the scan length distribution. Options are "uniform" and "zipfian"
   * (favoring short scans)
   */
  public static final String SCAN_LENGTH_DISTRIBUTION_PROPERTY = "scanlengthdistribution";

  /**
   * The default max scan length.
   */
  public static final String SCAN_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT = "uniform";

  /**
   * The name of the property for the order to insert records. Options are "ordered" or "hashed"
   */
  public static final String INSERT_ORDER_PROPERTY = "insertorder";

  /**
   * Default insert order.
   */
  public static final String INSERT_ORDER_PROPERTY_DEFAULT = "hashed";

  /**
   * Percentage data items that constitute the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION = "hotspotdatafraction";

  /**
   * Default value of the size of the hot set.
   */
  public static final String HOTSPOT_DATA_FRACTION_DEFAULT = "0.2";

  /**
   * Percentage operations that access the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION = "hotspotopnfraction";

  /**
   * Default value of the percentage operations accessing the hot set.
   */
  public static final String HOTSPOT_OPN_FRACTION_DEFAULT = "0.8";

  /**
   * How many times to retry when insertion of a single item to a DB fails.
   */
  public static final String INSERTION_RETRY_LIMIT = "core_workload_insertion_retry_limit";
  public static final String INSERTION_RETRY_LIMIT_DEFAULT = "0";

  /**
   * On average, how long to wait between the retries, in seconds.
   */
  public static final String INSERTION_RETRY_INTERVAL = "core_workload_insertion_retry_interval";
  public static final String INSERTION_RETRY_INTERVAL_DEFAULT = "3";

  public static final String CLIENT_ID = "clientid";
  public static final String CLIENT_ID_DEFAULT = "0";

  public static final char GEOHASH_CHARS[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

  protected long areaMinCode;
  protected long areaMaxCode;
  protected long hotspotMinCode;
  protected long hotspotMaxCode;

  protected Map<Integer, Random> randomNumGenMap;
  protected Map<Integer, LongStream> areaLocationHashGenMap;
  protected Map<Integer, LongStream> hotspotLocationHashGenMap;
  protected Map<Integer, PrimitiveIterator.OfLong> areaLocationHashItMap;
  protected Map<Integer, PrimitiveIterator.OfLong> hotspotLocationHashItMap;

  protected Map<String, Long> keyToLocationHashMap;
  protected Map<String, String> keyToMurmurHashMap;
  protected double maxLng;
  protected double maxLat;
  protected double minLat;
  protected double minLng;
  protected Map<String, Long> lastTimestampMap;
  protected NumberGenerator keysequence;
  protected DiscreteGenerator operationchooser;
  protected NumberGenerator keychooser;
  protected NumberGenerator fieldchooser;
  protected AcknowledgedCounterGenerator transactioninsertkeysequence;
  protected NumberGenerator scanlength;
  protected boolean orderedinserts;
  protected long fieldcount;
  protected long recordcount;
  protected int zeropadding;
  protected int insertionRetryLimit;
  protected int insertionRetryInterval;
  protected long workloadStartTime;
  protected long insertstart;
  protected int insertcount;
  protected String clientId;
  private Measurements measurements = Measurements.getMeasurements();
  protected String hotspotGeohash;
  protected String areaGeohash;
  protected Random randomNumGen;

  protected void saveMurmurHashForKey(String key, String murmurHash) {
    // TODO insert synchronization between threads here
    keyToMurmurHashMap.put (key, murmurHash);
  }

  protected void updateLastTimestamp (String key, long lastTimestamp) {
    lastTimestampMap.put (key, lastTimestamp);
  }

  /**
   * Initialize the scenario.
   * Called once, in the main client thread, before any operations are started.
   */
  @Override
  public void init(Properties p) throws WorkloadException {
    workloadStartTime = System.currentTimeMillis();
    table = p.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);
    keyToLocationHashMap = new ConcurrentHashMap<String, Long>();
    keyToMurmurHashMap = new ConcurrentHashMap<String, String>();
    lastTimestampMap = new ConcurrentHashMap<String, Long>();
    randomNumGen = new Random();

    areaMinCode = Long.parseLong(p.getProperty(AREA_MIN_PROPERTY, AREA_MIN_DEFAULT));
    areaMaxCode = Long.parseLong(p.getProperty(AREA_MAX_PROPERTY, AREA_MAX_DEFAULT));
    hotspotMinCode = Long.parseLong(p.getProperty(HOTSPOT_MIN_PROPERTY, HOTSPOT_MIN_DEFAULT));
    hotspotMaxCode = Long.parseLong(p.getProperty(HOTSPOT_MAX_PROPERTY, HOTSPOT_MAX_DEFAULT));
   
//    areaLocationHashGen = randomNumGen.longs(areaMinCode, areaMaxCode);
//    hotspotLocationHashGen = randomNumGen.longs(hotspotMinCode, hotspotMaxCode);
//    areaLocationHashIt = areaLocationHashGen.iterator();
//    hotspotLocationHashIt = hotspotLocationHashGen.iterator();
    randomNumGenMap = new ConcurrentHashMap<Integer, Random>();
    areaLocationHashGenMap = new ConcurrentHashMap<Integer, LongStream>();
    hotspotLocationHashGenMap = new ConcurrentHashMap<Integer, LongStream>();
    areaLocationHashItMap = new ConcurrentHashMap<Integer, PrimitiveIterator.OfLong>();
    hotspotLocationHashItMap = new ConcurrentHashMap<Integer, PrimitiveIterator.OfLong>();
 
    if (hotspotMinCode >= hotspotMaxCode) {
      System.err.println("HotspotMinCode and HotspotMaxCode are incompatible");
      System.exit(-1);
    }
    
    if (areaMinCode >= areaMaxCode) {
      System.err.println("AreaMinCode and AreaMaxCode are incompatible");
      System.exit(-1);
    }

    clientId = p.getProperty(CLIENT_ID, CLIENT_ID_DEFAULT);
    recordcount =
        Long.parseLong(p.getProperty(Client.RECORD_COUNT_PROPERTY, Client.DEFAULT_RECORD_COUNT));
    if (recordcount == 0) {
      recordcount = Integer.MAX_VALUE;
    }
    String requestdistrib =
        p.getProperty(REQUEST_DISTRIBUTION_PROPERTY, REQUEST_DISTRIBUTION_PROPERTY_DEFAULT);
    int maxscanlength =
        Integer.parseInt(p.getProperty(MAX_SCAN_LENGTH_PROPERTY, MAX_SCAN_LENGTH_PROPERTY_DEFAULT));
    String scanlengthdistrib =
        p.getProperty(SCAN_LENGTH_DISTRIBUTION_PROPERTY, SCAN_LENGTH_DISTRIBUTION_PROPERTY_DEFAULT);

    insertstart =
        Long.parseLong(p.getProperty(INSERT_START_PROPERTY, INSERT_START_PROPERTY_DEFAULT));
    insertcount=
        Integer.parseInt(p.getProperty(INSERT_COUNT_PROPERTY, String.valueOf(recordcount - insertstart)));
    // Confirm valid values for insertstart and insertcount in relation to recordcount
    if (recordcount < (insertstart + insertcount)) {
      System.err.println("Invalid combination of insertstart, insertcount and recordcount.");
      System.err.println("recordcount must be bigger than insertstart + insertcount.");
      System.exit(-1);
    }
    zeropadding =
      Integer.parseInt(p.getProperty(ZERO_PADDING_PROPERTY, ZERO_PADDING_PROPERTY_DEFAULT));

    readallfields = Boolean.parseBoolean(
        p.getProperty(READ_ALL_FIELDS_PROPERTY, READ_ALL_FIELDS_PROPERTY_DEFAULT));
    writeallfields = Boolean.parseBoolean(
        p.getProperty(WRITE_ALL_FIELDS_PROPERTY, WRITE_ALL_FIELDS_PROPERTY_DEFAULT));

    dataintegrity = Boolean.parseBoolean(
        p.getProperty(DATA_INTEGRITY_PROPERTY, DATA_INTEGRITY_PROPERTY_DEFAULT));

    if (p.getProperty(INSERT_ORDER_PROPERTY, INSERT_ORDER_PROPERTY_DEFAULT).compareTo("hashed") == 0) {
      orderedinserts = false;
    } else if (requestdistrib.compareTo("exponential") == 0) {
      double percentile = Double.parseDouble(p.getProperty(
            ExponentialGenerator.EXPONENTIAL_PERCENTILE_PROPERTY,
            ExponentialGenerator.EXPONENTIAL_PERCENTILE_DEFAULT));
      double frac = Double.parseDouble(p.getProperty(
            ExponentialGenerator.EXPONENTIAL_FRAC_PROPERTY,
            ExponentialGenerator.EXPONENTIAL_FRAC_DEFAULT));
      keychooser = new ExponentialGenerator(percentile, recordcount * frac);
    } else {
      orderedinserts = true;
    }

    keysequence = new CounterGenerator(insertstart);
    operationchooser = createOperationGenerator(p);

    transactioninsertkeysequence = new AcknowledgedCounterGenerator(recordcount);
    if (requestdistrib.compareTo("uniform") == 0) {
      keychooser = new UniformLongGenerator(insertstart, insertstart + insertcount - 1);
    } else if (requestdistrib.compareTo("sequential") == 0) {
      keychooser = new SequentialGenerator(insertstart, insertstart + insertcount - 1);
    } else if (requestdistrib.compareTo("zipfian") == 0) {
      // it does this by generating a random "next key" in part by taking the modulus over the
      // number of keys.
      // If the number of keys changes, this would shift the modulus, and we don't want that to
      // change which keys are popular so we'll actually construct the scrambled zipfian generator
      // with a keyspace that is larger than exists at the beginning of the test. that is, we'll predict
      // the number of inserts, and tell the scrambled zipfian generator the number of existing keys
      // plus the number of predicted keys as the total keyspace. then, if the generator picks a key
      // that hasn't been inserted yet, will just ignore it and pick another key. this way, the size of
      // the keyspace doesn't change from the perspective of the scrambled zipfian generator
      final double insertproportion = Double.parseDouble(
          p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
      int opcount = Integer.parseInt(p.getProperty(Client.OPERATION_COUNT_PROPERTY));
      int expectednewkeys = (int) ((opcount) * insertproportion * 2.0); // 2 is fudge factor

      keychooser = new ScrambledZipfianGenerator(insertstart, insertstart + insertcount + expectednewkeys);
    } else if (requestdistrib.compareTo("latest") == 0) {
      keychooser = new SkewedLatestGenerator(transactioninsertkeysequence);
    } else if (requestdistrib.equals("hotspot")) {
      double hotsetfraction =
        Double.parseDouble(p.getProperty(HOTSPOT_DATA_FRACTION, HOTSPOT_DATA_FRACTION_DEFAULT));
      double hotopnfraction =
        Double.parseDouble(p.getProperty(HOTSPOT_OPN_FRACTION, HOTSPOT_OPN_FRACTION_DEFAULT));
      keychooser = new HotspotIntegerGenerator(insertstart, insertstart + insertcount - 1,
          hotsetfraction, hotopnfraction);
    } else if (requestdistrib.equals("constant")) {
      keychooser = new ConstantIntegerGenerator((int)insertstart);
    } else {
      throw new WorkloadException("Unknown request distribution \"" + requestdistrib + "\"");
    }

    fieldchooser = new UniformLongGenerator(0, fieldcount - 1);

    if (scanlengthdistrib.compareTo("uniform") == 0) {
      scanlength = new UniformLongGenerator(1, maxscanlength);
    } else if (scanlengthdistrib.compareTo("zipfian") == 0) {
      scanlength = new ZipfianGenerator(1, maxscanlength);
    } else {
      throw new WorkloadException(
          "Distribution \"" + scanlengthdistrib + "\" not allowed for scan length");
    }

    insertionRetryLimit = Integer.parseInt(p.getProperty(
          INSERTION_RETRY_LIMIT, INSERTION_RETRY_LIMIT_DEFAULT));
    insertionRetryInterval = Integer.parseInt(p.getProperty(
          INSERTION_RETRY_INTERVAL, INSERTION_RETRY_INTERVAL_DEFAULT));
  }

  protected String buildKeyName(long keynum) {
    if (!orderedinserts) {
      keynum = Utils.hash(keynum);
    }
    String value = Long.toString(keynum);
    int fill = zeropadding - value.length();
    String prekey = "user";
    for (int i = 0; i < fill; i++) {
      prekey += '0';
    }
    return prekey + value;
  }

/*  protected String getRandomGeohashString(int length) {
    String result = "";
    if (length <= 0) return result;
    Random random = new Random();
    for (int i = 0; i < length; i++) {
      int idx = random.nextInt(GEOHASH_CHARS.length);
      result = result + GEOHASH_CHARS[idx];
    }
    return result;
  }
 */
 /* protected String calculateGeohash(String key, int precision) {
    float rand = randomNumGen.nextFloat();
    boolean insideHotspot = false;
    if (rand < 0.8)
      insideHotspot = true;

    String geohash = "";
    if (insideHotspot) {
      geohash = hotspotGeohash;
      String finerDetail = getRandomGeohashString(precision - geohash.length());
      geohash = geohash + finerDetail;
    } else {
      do {
        geohash = areaGeohash;
        String finerDetail = getRandomGeohashString(precision - geohash.length());
        geohash = geohash + finerDetail;
      } while (geohash.startsWith(hotspotGeohash));
    }
    return geohash;
  }*/

/*  protected String buildPartitionKeyValue (String key, long keynum) {
    if (partitionKeyType.equals("hybrid")) {
      String geohash = null;
      if (keyToGeohashMap.containsKey(key)) {
        geohash = keyToGeohashMap.get(key);
      } else {  
        geohash = calculateGeohash(key, geohashPrecision);
        saveGeohashForKey(key, geohash);
      }
      String murmurHash = null;
      if (keyToMurmurHashMap.containsKey(key)) {
        murmurHash = keyToMurmurHashMap.get(key);
      } else {  
        murmurHash = calculateMurmurHash(key);
        saveMurmurHashForKey(key, murmurHash);
      }
      String partitionKey = geohash+murmurHash;
      return partitionKey;
    } else if (partitionKeyType.equals("geohash_only")) {
      String geohash = null;
      if(keyToGeohashMap.containsKey(key)) {
        geohash = keyToGeohashMap.get(key);
      } else {
        geohash = calculateGeohash(key, geohashPrecision);
        saveGeohashForKey(key, geohash);
      }
      return geohash;
    }
    return null;
  }*/

/*  protected String calculateMurmurHash (String key) {
    long hashNum = MurmurHash.hash (key.getBytes(), key.length(), 0);
    String result = Long.toString(hashNum);
    for (int i=result.length();i<MurmurHash.MAX_LENGTH;i++) {
      result = "0"+result;
    }
    return result;
  }
*/

  long calculateLocationHash(String key, int threadId) {
    float rand = randomNumGenMap.get(threadId).nextFloat();
    boolean insideHotspot = false;
    if (rand < 0.8)
      insideHotspot = true;

    long locationHash = 0;
    if (insideHotspot) {
      locationHash = hotspotLocationHashItMap.get(threadId).nextLong();
    } else {
      do {
        locationHash = areaLocationHashItMap.get(threadId).nextLong();
      } while (locationHash <= hotspotMaxCode && locationHash >= hotspotMinCode);
    }
    return locationHash;
  }

  void saveLocationHashForKey(long locationHash, String key) {
    keyToLocationHashMap.put(key, locationHash);
  }

  long getLocationHash(String key, long keynum, int threadId) {
    if (keyToLocationHashMap.containsKey(key)) {
      return keyToLocationHashMap.get(key);
    } else {
      long locationHash = calculateLocationHash(key, threadId);
      saveLocationHashForKey(locationHash, key);
      return locationHash;
    }
  }

  /**
   * Builds values for all fields.
   */
  private HashMap<String, ByteIterator> buildValues(String key, long keynum, Object threadstate) {
    int threadId = (Integer) threadstate;
    HashMap<String, ByteIterator> values = new HashMap<>();
    long locationHash = getLocationHash(key, keynum, threadId);
    //System.out.println("Location hash = " + locationHash);
    values.put(LOCATION_HASH_COLUMN_NAME, new NumericByteIterator(locationHash));
    //values.put(LOCATION_HASH_COLUMN_NAME, new StringByteIterator(Long.toString(locationHash)));
    
    long numericTime = System.currentTimeMillis();
    String time = Long.toString(numericTime);
    String keyValue = time;
    updateLastTimestamp(key, numericTime);
    values.put(TIMESTAMP_COLUMN_NAME, new StringByteIterator(keyValue));
    return values;
  }

  /**
   * Build a deterministic value given the key information.
   */
  /*private String buildDeterministicValue(String key, String fieldkey) {
    int size = fieldlengthgenerator.nextValue().intValue();
    StringBuilder sb = new StringBuilder(size);
    sb.append(key);
    sb.append(':');
    sb.append(fieldkey);
    while (sb.length() < size) {
    sb.append(':');
    sb.append(sb.toString().hashCode());
    }
    sb.setLength(size);

    return sb.toString();
    }*/

  /**
   * Do one insert operation. Because it will be called concurrently from multiple client threads,
   * this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
    public boolean doInsert(DB db, Object threadstate) {
      int keynum = keysequence.nextValue().intValue();
      String dbkey = buildKeyName(keynum);
      HashMap<String, ByteIterator> values = buildValues(dbkey, keynum, threadstate);

      Status status;
      int numOfRetries = 0;
      do {
        status = db.insert(table, dbkey, values);

        // Now need to log the completion time of the insert along with the TS value that got inserted
        long currentTime = System.currentTimeMillis();
//        System.out.println ("curr_ts "+currentTime+" finish_insert key "+dbkey
//            +" ts "+((StringByteIterator)values.get(TIMESTAMP_COLUMN_NAME)).toString());

        if (null != status && status.isOk()) {
          break;
        }
        // Retry if configured. Without retrying, the load process will fail
        // even if one single insertion fails. User can optionally configure
        // an insertion retry limit (default is 0) to enable retry.
        if (++numOfRetries <= insertionRetryLimit) {
          System.err.println("Retrying insertion, retry count: " + numOfRetries);
          try {
            // Sleep for a random number between [0.8, 1.2)*insertionRetryInterval.
            int sleepTime = (int) (1000 * insertionRetryInterval * (0.8 + 0.4 * Math.random()));
            Thread.sleep(sleepTime);
          } catch (InterruptedException e) {
            break;
          }

        } else {
          System.err.println("Error inserting, not retrying any more. number of attempts: " + numOfRetries +
              "Insertion Retry Limit: " + insertionRetryLimit);
          break;

        }
      } while (true);

      return null != status && status.isOk();
    }

  /**
   * Do one transaction operation. Because it will be called concurrently from multiple client
   * threads, this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
    public boolean doTransaction(DB db, Object threadstate) {
      String operation = operationchooser.nextString();
      if(operation == null) {
        return false;
      }

      switch (operation) {
        case "READ":
          doTransactionRead(db, threadstate);
          break;
        case "UPDATE":
          doTransactionUpdate(db, threadstate);
          break;
        case "INSERT":
          doTransactionInsert(db, threadstate);
          break;
        case "SCAN":
          doTransactionScan(db, threadstate);
          break;
        default:
          doTransactionReadModifyWrite(db, threadstate);
      }

      return true;
    }

  /**
   * Results are reported in the first three buckets of the histogram under
   * the label "VERIFY".
   * Bucket 0 means the expected data was returned.
   * Bucket 1 means incorrect data was returned.
   * Bucket 2 means null data was returned when some data was expected.
   */
  protected void verifyRow(String key, HashMap<String, ByteIterator> cells, long startTimestamp) {
    Status verifyStatus = Status.OK;
    long startTime = System.nanoTime();
    if(!cells.isEmpty()) {
      //long retrievedTs = ((NumericByteIterator)cells.get(TIMESTAMP_COLUMN_NAME)).getLong();
      //long retrievedTs = Utils.bytesToLong(cells.get(TIMESTAMP_COLUMN_NAME).toArray());
      //long retrievedTs =Long.parseLong( cells.get(TIMESTAMP_COLUMN_NAME).toString());
      String retrievedTs = cells.get(TIMESTAMP_COLUMN_NAME).toString();
      String retrievedTime = retrievedTs.split("\\|")[1];
      if(lastTimestampMap.containsKey(key)) {
        long expectedTs = lastTimestampMap.get(key);
        try{
          if (Long.parseLong(retrievedTime) < workloadStartTime)
            return; 
        } catch (Exception e) {
          System.out.println ("Exception parsing retrievedTs : " + retrievedTs);
        }

//        System.out.println ("curr_ts "+System.currentTimeMillis() + " read_ts key "+key+" start_ts "+startTimestamp + " ret_ts "+ retrievedTs);
        if (expectedTs != Long.parseLong(retrievedTime))
          verifyStatus = Status.UNEXPECTED_STATE;
      } else {
      }
    } else {
      verifyStatus = Status.ERROR;
    }

    /*    if (!cells.isEmpty()) {
          for (Map.Entry<String, ByteIterator> entry : cells.entrySet()) {
          if (!entry.getValue().toString().equals(buildDeterministicValue(key, entry.getKey()))) {
          verifyStatus = Status.UNEXPECTED_STATE;
          break;
          }
          }
          } else {
    // This assumes that null data is never valid
    verifyStatus = Status.ERROR;
    }*/
    long endTime = System.nanoTime();
    measurements.measure("VERIFY", (int) (endTime - startTime) / 1000);
    measurements.reportStatus("VERIFY", verifyStatus);
  }

  long nextKeynum() {
    long keynum;
    if (keychooser instanceof ExponentialGenerator) {
      do {
        keynum = transactioninsertkeysequence.lastValue() - keychooser.nextValue().intValue();
      } while (keynum < 0);
    } else {
      do {
        keynum = keychooser.nextValue().intValue();
      } while (keynum > transactioninsertkeysequence.lastValue());
    }
    return keynum;
  }

  public void doTransactionRead(DB db, Object threadstate) {
    // choose a random key
    long keynum = nextKeynum();

/*    String keyname = buildKeyName(keynum);

    HashSet<String> fields = null;

    //String partitionKey = buildPartitionKeyValue(keyname, keynum);

    Map<String, ByteIterator> values = new HashMap<String, ByteIterator>();
    values.put (PARTITION_KEY_COLUMN_NAME, new StringByteIterator(partitionKey));

    HashMap<String, ByteIterator> cells = new HashMap<String, ByteIterator>();
    long readStartTime = System.currentTimeMillis();
    db.read(table, keyname, fields, cells, values);

    verifyRow(keyname, cells, readStartTime);*/
  }

  public void doTransactionReadModifyWrite(DB db, Object threadstate) {
    // choose a random key
    long keynum = nextKeynum();

    String keyname = buildKeyName(keynum);

    HashSet<String> fields = null;
    HashMap<String, ByteIterator> values;

    // new data for all the fields
    values = buildValues(keyname, keynum, threadstate);

    // do the transaction

    HashMap<String, ByteIterator> cells = new HashMap<String, ByteIterator>();

    long ist = measurements.getIntendedtartTimeNs();
    long st = System.nanoTime();
    long readStartTime = System.currentTimeMillis();
    db.read(table, keyname, fields, cells, values);

    db.update(table, keyname, values);
    // Now need to log the completion time of the update along with the TS value that got inserted
    long currentTime = System.currentTimeMillis();
//    System.out.println ("curr_ts "+currentTime+" finish_update key "+keyname
//        +" ts "+((StringByteIterator)values.get(TIMESTAMP_COLUMN_NAME)).toString());


    long en = System.nanoTime();

    verifyRow(keyname, cells, readStartTime);

    measurements.measure("READ-MODIFY-WRITE", (int) ((en - st) / 1000));
    measurements.measureIntended("READ-MODIFY-WRITE", (int) ((en - ist) / 1000));
  }

  public void doTransactionScan(DB db, Object threadstate) {
    // choose a random key
    long keynum = nextKeynum();

    String startkeyname = buildKeyName(keynum);

    // choose a random scan length
    int len = scanlength.nextValue().intValue();

    HashSet<String> fields = null;
    db.scan(table, startkeyname, len, fields, new Vector<HashMap<String, ByteIterator>>());
  }

  public void doTransactionUpdate(DB db, Object threadstate) {
    // choose a random key
    long keynum = nextKeynum();

    String keyname = buildKeyName(keynum);

    HashMap<String, ByteIterator> values;

    // new data for all the fields
    values = buildValues(keyname, keynum, threadstate);

    db.update(table, keyname, values);
    // Now need to log the completion time of the update along with the TS value that got inserted
    long currentTime = System.currentTimeMillis();
//    System.out.println ("curr_ts "+currentTime+" finish_update key "+keyname
//        +" ts "+((StringByteIterator)values.get(TIMESTAMP_COLUMN_NAME)).toString());

  }

  public void doTransactionInsert(DB db, Object threadstate) {
    // choose the next key
    long keynum = transactioninsertkeysequence.nextValue();

    try {
      String dbkey = buildKeyName(keynum);

      HashMap<String, ByteIterator> values = buildValues(dbkey, keynum, threadstate);
      db.insert(table, dbkey, values);
    } finally {
      transactioninsertkeysequence.acknowledge(keynum);
    }
  }

  /**
   * Creates a weighted discrete values with database operations for a workload to perform.
   * Weights/proportions are read from the properties list and defaults are used
   * when values are not configured.
   * Current operations are "READ", "UPDATE", "INSERT", "SCAN" and "READMODIFYWRITE".
   *
   * @param p The properties list to pull weights from.
   * @return A generator that can be used to determine the next operation to perform.
   * @throws IllegalArgumentException if the properties object was null.
   */
  protected static DiscreteGenerator createOperationGenerator(final Properties p) {
    if (p == null) {
      throw new IllegalArgumentException("Properties object cannot be null");
    }
    final double readproportion = Double.parseDouble(
        p.getProperty(READ_PROPORTION_PROPERTY, READ_PROPORTION_PROPERTY_DEFAULT));
    final double updateproportion = Double.parseDouble(
        p.getProperty(UPDATE_PROPORTION_PROPERTY, UPDATE_PROPORTION_PROPERTY_DEFAULT));
    final double insertproportion = Double.parseDouble(
        p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
    final double scanproportion = Double.parseDouble(
        p.getProperty(SCAN_PROPORTION_PROPERTY, SCAN_PROPORTION_PROPERTY_DEFAULT));
    final double readmodifywriteproportion = Double.parseDouble(p.getProperty(
          READMODIFYWRITE_PROPORTION_PROPERTY, READMODIFYWRITE_PROPORTION_PROPERTY_DEFAULT));

    final DiscreteGenerator operationchooser = new DiscreteGenerator();
    if (readproportion > 0) {
      operationchooser.addValue(readproportion, "READ");
    }

    if (updateproportion > 0) {
      operationchooser.addValue(updateproportion, "UPDATE");
    }

    if (insertproportion > 0) {
      operationchooser.addValue(insertproportion, "INSERT");
    }

    if (scanproportion > 0) {
      operationchooser.addValue(scanproportion, "SCAN");
    }

    if (readmodifywriteproportion > 0) {
      operationchooser.addValue(readmodifywriteproportion, "READMODIFYWRITE");
    }
    return operationchooser;
  }

  @Override
  public Object initThread(Properties p, int mythreadid, int threadcount) throws WorkloadException {
    Random rand = new Random(mythreadid);
    randomNumGenMap.put(mythreadid, rand);

    areaLocationHashGenMap.put(mythreadid, rand.longs(areaMinCode, areaMaxCode));
    hotspotLocationHashGenMap.put(mythreadid, rand.longs(hotspotMinCode, hotspotMaxCode));

    areaLocationHashItMap.put(mythreadid, areaLocationHashGenMap.get(mythreadid).iterator());    
    hotspotLocationHashItMap.put(mythreadid, hotspotLocationHashGenMap.get(mythreadid).iterator());    

    return new Integer(mythreadid);
  }

}
