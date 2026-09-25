package com.kun.mianshikun.model.dto.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import javax.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

/**
 * 验证 A 方案：实体注解能为「新建索引」生成带 IK 分词的 mapping。
 * 做法：用框架按注解生成 mapping -> 建到临时索引 -> 读回断言 -> 删除临时索引。
 */
@SpringBootTest
class QuestionEsMappingTest {

    private static final String TEST_INDEX = "question_mapping_test";

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object> mapping) {
        Map<String, Object> result = (Map<String, Object>) mapping.get("properties");
        assertNotNull(result, "mapping 中应包含 properties");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(Map<String, Object> properties, String name) {
        Map<String, Object> f = (Map<String, Object>) properties.get(name);
        assertNotNull(f, "mapping 中应包含字段: " + name);
        return f;
    }

    @Test
    @DisplayName("实体注解生成的 mapping 含 IK 分词配置（title/content/answer + keyword 字段）")
    void annotationProducesIkMapping() {
        // 1) 按实体注解生成 mapping
        IndexOperations entityOps = elasticsearchRestTemplate.indexOps(QuestionEsDTO.class);
        Document mapping = entityOps.createMapping();
        assertNotNull(mapping, "应能按注解生成 mapping");

        // 2) 建到临时索引
        IndexOperations testOps = elasticsearchRestTemplate.indexOps(IndexCoordinates.of(TEST_INDEX));
        try {
            if (testOps.exists()) {
                testOps.delete();
            }
            // 注意：Document 是 Map 的子类，必须用 create(settings, mapping) 两参重载，
            // 否则 mapping 会被当作 settings 传入（ES 报 unknown setting [index.properties...]）
            Settings settings = entityOps.createSettings();
            assertTrue(testOps.create(settings, mapping), "临时索引创建应成功");

            // 3) 读回并断言
            Map<String, Object> actual = testOps.getMapping();
            Map<String, Object> properties = props(actual);

            for (String textField : new String[]{"title", "content", "answer"}) {
                Map<String, Object> f = field(properties, textField);
                assertEquals("text", f.get("type"), textField + " 应为 text 类型");
                assertEquals("ik_max_word", f.get("analyzer"), textField + " 索引分词器应为 ik_max_word");
                assertEquals("ik_smart", f.get("search_analyzer"), textField + " 查询分词器应为 ik_smart");
            }

            assertEquals("keyword", field(properties, "tags").get("type"), "tags 应为 keyword");
            assertEquals("keyword", field(properties, "isDelete").get("type"), "isDelete 应为 keyword");
            assertEquals("long", field(properties, "userId").get("type"), "userId 应为 long");
            assertEquals("date", field(properties, "createTime").get("type"), "createTime 应为 date");
            assertEquals("yyyy-MM-dd HH:mm:ss", field(properties, "createTime").get("format"),
                    "日期格式应与实体注解一致");

            System.out.println("[A 验证] 注解生成的 mapping 含 IK 配置："
                    + "title/content/answer = ik_max_word + ik_smart，tags/isDelete = keyword");
        } finally {
            // 4) 清理临时索引
            if (testOps.exists()) {
                testOps.delete();
                System.out.println("[A 验证] 临时索引 " + TEST_INDEX + " 已删除");
            }
        }
    }
}
