#!/bin/bash
bin/ycsb load cassandra-cql -p hosts="127.0.0.1" -p workload=com.yahoo.ycsb.workloads.FogstoreBenchmark -P workloads/workload-const > load.out
sleep 1
bin/ycsb run cassandra-cql -p hosts="127.0.0.1" -p workload=com.yahoo.ycsb.workloads.FogstoreBenchmark -P workloads/workload-const > run1.out &
pid1=$!
bin/ycsb run cassandra-cql -p hosts="127.0.0.1" -p workload=com.yahoo.ycsb.workloads.FogstoreBenchmark -P workloads/workload-const > run2.out &
pid2=$!
wait $pid1
wait $pid2
