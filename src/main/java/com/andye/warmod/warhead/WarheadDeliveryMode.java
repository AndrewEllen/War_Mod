package com.andye.warmod.warhead;public enum WarheadDeliveryMode{SINGLE,CLUSTER_FOUR;public int childCount(){return this==CLUSTER_FOUR?4:1;}}
