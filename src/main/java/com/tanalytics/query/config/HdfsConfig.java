package com.tanalytics.query.config;

import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;

/**
 * Configures the Hadoop HDFS FileSystem client for accessing Parquet files.
 */
@Configuration
public class HdfsConfig {

    @Value("${hdfs.namenode:hdfs://namenode:9000}")
    private String namenode;

    @Value("${hdfs.user:root}")
    private String user;

    @Bean
    public FileSystem hdfsFileSystem() throws IOException, InterruptedException {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", namenode);
        conf.set("dfs.client.use.datanode.hostname", "true");
        return FileSystem.get(URI.create(namenode), conf, user);
    }
}
