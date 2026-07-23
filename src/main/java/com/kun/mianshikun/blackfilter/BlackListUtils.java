package com.kun.mianshikun.blackfilter;

import cn.hutool.bloomfilter.BitMapBloomFilter;
import cn.hutool.core.collection.CollUtil;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Slf4j
public class BlackListUtils {
    private static BitMapBloomFilter bloomFilter;
    public static boolean isBlack(String str) {
        return bloomFilter.contains(str);
    }
    public static void rebuild(String config) {
        if (StringUtils.isBlank( config)){
            config="{}";
        }
        Yaml yaml = new Yaml();
        Map map = yaml.loadAs(config, Map.class);
        List<String> blackList = (List<String>) map.get("blackIpList");
        synchronized (BlackListUtils.class){
            if (!CollUtil.isEmpty(blackList)){
                bloomFilter = new BitMapBloomFilter(100);
                for (String s : blackList) {
                    bloomFilter.add(s);
                }
            }else {
                bloomFilter = new BitMapBloomFilter(100);
            }
        }
    }
}
