ycsb-tools
==========

YCSB, build tools, workload files, scripts, etc. for doing testing. 

Small wrapper around YCSB load driver that makes it easier to use.  

- uses our patched YCSB driver that conforms to best practices for mongodb and uses our latest driver 
- breaks workload definitions into "dataset" files which define the number of documents and insertorder vs. "workload" files which define the mix of read/write/update operations 
- moved commonly changed attributes into a config file so it's easy to use the same settings across a number of tests 
- organize workload output so it's easy to remember what settings were used 
- added common presplit scripts that prepare the database for various sharded setups 

Usage
===== 

# git clone this repo onto your computer 
# edit the "config" file to match the settings you want to use 
# ./bin/load to load your dataset into the target database 
# ./bin/run to execute the workloads, saving output into the "output" directory 



