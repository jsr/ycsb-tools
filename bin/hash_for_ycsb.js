db.getSiblingDB("ycsb").dropDatabase();
sh.enableSharding("ycsb");
sh.shardCollection("ycsb.usertable",{"_id":'hashed'});
db.getSiblingDB("ycsb").runCommand( {"collMod" : "usertable" , "usePowerOf2Sizes" : false } );
sh.setBalancerState(false); 
sh.waitForBalancerOff();
