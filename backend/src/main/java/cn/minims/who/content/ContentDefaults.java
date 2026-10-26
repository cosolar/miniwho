package cn.minims.who.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cn.minims.who.util.Json;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContentDefaults {

    private static final Map<String, String> EN_TITLES = Map.ofEntries(
            Map.entry(SectionTypes.BASIC, "Basic Information"),
            Map.entry(SectionTypes.INTENTION, "Job Intention"),
            Map.entry(SectionTypes.EDUCATION, "Education"),
            Map.entry(SectionTypes.WORK, "Experience"),
            Map.entry(SectionTypes.PROJECT, "Projects"),
            Map.entry(SectionTypes.CAMPUS, "Campus"),
            Map.entry(SectionTypes.SKILLS, "Skills"),
            Map.entry(SectionTypes.PROFICIENCY, "Proficiency"),
            Map.entry(SectionTypes.PAPER, "Publications"),
            Map.entry(SectionTypes.PORTFOLIO, "Portfolio"),
            Map.entry(SectionTypes.SELF, "Summary"),
            Map.entry(SectionTypes.POSTGRADUATE, "Graduate Application"),
            Map.entry(SectionTypes.COMPETITION, "Competitions"),
            Map.entry(SectionTypes.AWARD, "Awards"),
            Map.entry(SectionTypes.CERTIFICATE, "Certificates"),
            Map.entry(SectionTypes.JOURNAL, "Papers"),
            Map.entry(SectionTypes.HOBBY, "Interests"),
            Map.entry(SectionTypes.CUSTOM, "Custom Section")
    );

    private final Json json;

    public ContentDefaults(Json json) {
        this.json = json;
    }

    public ObjectNode blankContent(String language) {
        boolean en = "en".equalsIgnoreCase(language);
        ObjectNode content = json.object();
        content.put("schemaVersion", 1);
        content.put("language", en ? "en" : "zh");
        ArrayNode sections = json.array();
        List<String> order = SectionTypes.CORE_ORDER;
        for (int i = 0; i < order.size(); i++) {
            String type = order.get(i);
            boolean visible = !SectionTypes.PAPER.equals(type);
            sections.add(blankSection(type, i, visible, en));
        }
        content.set("sections", sections);
        return content;
    }

    public ObjectNode blankSection(String type, int order, boolean visible, boolean en) {
        ObjectNode section = json.object();
        section.put("id", "sec_" + type);
        section.put("type", type);
        section.put("title", en ? EN_TITLES.getOrDefault(type, SectionTypes.titleOf(type)) : SectionTypes.titleOf(type));
        section.put("visible", visible);
        section.put("order", order);
        ObjectNode meta = json.object();
        if (SectionTypes.INTENTION.equals(type)) {
            meta.put("displayMode", "title_only");
        }
        if (SectionTypes.isCustom(type)) {
            meta.put("customTitle", "自定义模块");
        }
        section.set("meta", meta);
        ArrayNode items = json.array();
        if (SectionTypes.SINGLE_ITEM_TYPES.contains(type)) {
            ObjectNode item = json.object();
            item.put("id", "itm_" + type);
            ObjectNode fields = json.object();
            if (SectionTypes.BASIC.equals(type)) {
                fields.put("name", "");
                fields.put("gender", "");
                fields.put("showGender", true);
                fields.put("phone", "");
                fields.put("email", "");
                fields.put("identity", "");
                fields.put("city", "");
                fields.put("birthday", "");
                fields.put("showAge", false);
                fields.put("nativePlace", "");
                fields.put("ethnicity", "");
                fields.put("politics", "");
                fields.put("maritalStatus", "");
                fields.put("website", "");
            } else if (SectionTypes.INTENTION.equals(type)) {
                fields.put("jobTitle", "");
                fields.put("city", "");
                fields.put("salaryMin", "");
                fields.put("salaryMax", "");
                fields.put("salaryNegotiable", false);
                fields.put("status", "");
            } else if (SectionTypes.POSTGRADUATE.equals(type)) {
                fields.put("school", "");
                fields.put("major", "");
                fields.put("year", "");
                fields.put("initialScore", "");
                fields.put("finalScore", "");
                fields.put("note", "");
            } else {
                fields.put("content", "");
            }
            item.set("fields", fields);
            items.add(item);
        }
        section.set("items", items);
        return section;
    }

    public ObjectNode defaultPrefs(JsonNode content) {
        ObjectNode prefs = json.object();
        ArrayNode order = json.array();
        ArrayNode hidden = json.array();
        for (JsonNode section : content.path("sections")) {
            order.add(section.path("type").asText());
            if (!section.path("visible").asBoolean(true)) {
                hidden.add(section.path("type").asText());
            }
        }
        prefs.set("order", order);
        prefs.set("hidden", hidden);
        prefs.set("customTitles", json.object());
        return prefs;
    }

    public ObjectNode defaultStyle(String templateCode) {
        ObjectNode style = json.object();
        style.put("templateCode", templateCode == null || templateCode.isBlank() ? "qinghe" : templateCode);
        style.put("filter", "standard");
        style.put("fontSizeStep", 0);
        style.put("spacingStep", 0);
        style.put("showBrandFooter", true);
        return style;
    }

    public ObjectNode sampleContent() {
        try (InputStream input = new ClassPathResource("seed/demo-resume.json").getInputStream()) {
            String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return (ObjectNode) json.read(raw);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取示例简历数据", e);
        }
    }

    public Map<String, String> englishTitles() {
        return new LinkedHashMap<>(EN_TITLES);
    }
}
