package cn.minims.who.content;

import com.fasterxml.jackson.databind.JsonNode;
import cn.minims.who.util.Json;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ScoreEngine {

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final List<String> WEAK_VERBS = List.of("负责", "参与", "协助", "帮助", "了解");
    private static final List<String> STRONG_VERBS = List.of("主导", "搭建", "设计", "优化", "推动", "落地", "提升", "重构", "上线", "建立");
    private static final List<String> GENERIC_TITLE_WORDS = List.of("工程师", "开发", "经理", "产品", "运营", "专员", "主管", "实习生", "岗位");

    private final Json json;

    public ScoreEngine(Json json) {
        this.json = json;
    }

    public Analysis analyze(JsonNode content) {
        Map<String, JsonNode> visible = new HashMap<>();
        for (JsonNode section : content.path("sections")) {
            if (section.path("visible").asBoolean(true)) {
                visible.put(section.path("type").asText(), section);
            }
        }

        JsonNode basic = fields(visible.get(SectionTypes.BASIC));
        JsonNode intention = fields(visible.get(SectionTypes.INTENTION));
        String name = text(basic, "name");
        String phone = text(basic, "phone");
        String email = text(basic, "email");
        String identity = text(basic, "identity");
        String jobTitle = text(intention, "jobTitle");
        String skills = plain(fields(visible.get(SectionTypes.SKILLS)), "content");
        String self = plain(fields(visible.get(SectionTypes.SELF)), "content");

        List<Entry> entries = new ArrayList<>();
        collectEntries(visible.get(SectionTypes.WORK), SectionTypes.WORK, entries);
        collectEntries(visible.get(SectionTypes.PROJECT), SectionTypes.PROJECT, entries);
        collectEntries(visible.get(SectionTypes.CAMPUS), SectionTypes.CAMPUS, entries);
        String allDescriptions = String.join("\n", entries.stream().map(Entry::content).toList());

        boolean hasEducation = hasSchool(visible.get(SectionTypes.EDUCATION));
        boolean hasExperience = entries.stream().anyMatch(e -> !e.content().isBlank());
        boolean hasExtra = hasExtra(visible);
        int completeness = 0;
        if (!name.isBlank()) completeness += 10;
        if (!phone.isBlank()) completeness += 10;
        if (!email.isBlank()) completeness += 10;
        if (!identity.isBlank()) completeness += 10;
        if (!jobTitle.isBlank()) completeness += 10;
        if (hasEducation) completeness += 10;
        if (hasExperience) completeness += 10;
        if (skills.length() >= 10) completeness += 10;
        if (self.length() >= 10) completeness += 10;
        if (hasExtra) completeness += 10;

        List<Issue> issues = new ArrayList<>();
        List<Dimension> dimensions = new ArrayList<>();

        int completenessScore = Math.round(completeness * 0.25f);
        dimensions.add(new Dimension("completeness", "完整度", completenessScore, 25,
                "已完成 " + completeness / 10 + " / 10 项核心信息"));
        if (name.isBlank()) addIssue(issues, "basic_info", "name", "high", "缺少姓名", "简历头部没有名字，招聘方无法快速识别。", "在「基本信息」中填写真实姓名。");
        if (phone.isBlank()) addIssue(issues, "basic_info", "phone", "high", "缺少手机号", "联系方式是投递后的必要信息。", "补充常用手机号，分享页可单独隐藏。");
        if (email.isBlank()) addIssue(issues, "basic_info", "email", "high", "缺少邮箱", "不少企业会通过邮箱发送笔试和面试通知。", "补充常用邮箱并检查拼写。");
        if (jobTitle.isBlank()) addIssue(issues, "job_intention", "jobTitle", "high", "没有写期望职位", "目标不清晰会让筛选者难以判断匹配度。", "填写一个明确的期望职位，例如「后端开发工程师」。");
        if (!hasEducation) addIssue(issues, "education", "school", "high", "教育背景不完整", "至少需要一所学校的名称、专业与就读时间。", "补充学校、专业、学历和起止时间。");
        if (!hasExperience) addIssue(issues, "work_experience", "content", "high", "没有可展示的履历", "工作、项目或在校经历是简历的主体内容。", "从最相关的一段履历开始填写，优先写清职责与结果。");

        int jobMatch = 0;
        if (!jobTitle.isBlank()) jobMatch += 10;
        if (skills.length() >= 10) jobMatch += 5;
        List<String> titleTokens = titleTokens(jobTitle);
        boolean tokenHit = titleTokens.stream().anyMatch(t -> allDescriptions.contains(t));
        if (tokenHit) jobMatch += 5;
        if (allDescriptions.length() >= 80) jobMatch += 3;
        if (hasExtra) jobMatch += 2;
        jobMatch = Math.min(25, jobMatch);
        dimensions.add(new Dimension("job_match", "岗位匹配", jobMatch, 25,
                jobTitle.isBlank() ? "填写期望职位后可评估" : (tokenHit ? "履历中出现了目标岗位关键词" : "岗位关键词在履历中体现不足")));
        if (!jobTitle.isBlank() && !tokenHit && !allDescriptions.isBlank()) {
            addIssue(issues, "project_experience", "content", "low", "岗位关键词覆盖不足", "履历描述里没有体现与「" + jobTitle + "」直接相关的关键词。", "在职责与成果中自然补充与目标岗位相关的技术、方法或业务词。");
        }

        int weakCount = 0;
        int weakWithoutNumber = 0;
        for (String verb : WEAK_VERBS) {
            int index = allDescriptions.indexOf(verb);
            while (index >= 0) {
                weakCount++;
                String tail = allDescriptions.substring(index, Math.min(allDescriptions.length(), index + 80));
                if (!DIGIT.matcher(tail).find()) weakWithoutNumber++;
                index = allDescriptions.indexOf(verb, index + verb.length());
            }
        }
        int longParagraphs = 0;
        for (Entry entry : entries) {
            if (entry.content().length() > 260) longParagraphs++;
        }
        boolean firstPerson = allDescriptions.contains("我");
        int expression = 20;
        if (weakWithoutNumber > 0) expression -= Math.min(6, weakWithoutNumber * 2);
        if (!allDescriptions.isBlank() && !DIGIT.matcher(allDescriptions).find()) expression -= 2;
        expression -= Math.min(4, longParagraphs * 2);
        if (firstPerson) expression -= 2;
        expression = Math.max(hasExperience ? 8 : 5, Math.min(20, expression));
        dimensions.add(new Dimension("expression", "表达质量", expression, 20,
                weakCount > 0 ? "检测到 " + weakCount + " 处「负责 / 参与」类弱表达" : "表达整体清晰"));
        if (weakWithoutNumber > 0) {
            addIssue(issues, entries.isEmpty() ? "work_experience" : entries.get(0).sectionType(), "content", "medium",
                    "弱动词缺少结果支撑", "描述中多次出现「负责 / 参与 / 协助」，但没有对应的规模、结果或数字。",
                    "用动作 + 做法 + 结果改写，例如「主导订单查询重构，P95 从 820ms 降至 210ms」。");
        }
        if (longParagraphs > 0) {
            addIssue(issues, entries.get(0).sectionType(), "content", "low", "段落偏长", "单段履历超过 260 字，阅读时容易抓不到重点。", "拆成 2-3 条要点，每条只讲一个成果。");
        }
        if (firstPerson && hasExperience) {
            addIssue(issues, entries.get(0).sectionType(), "content", "low", "出现第一人称", "简历通常省略「我」，直接以动作开头更简洁。", "删除「我」并调整句式为动词开头。");
        }

        long quantified = entries.stream().filter(e -> DIGIT.matcher(e.content()).find()).count();
        int quantification = entries.isEmpty() ? 0 : Math.round(15f * quantified / entries.size());
        dimensions.add(new Dimension("quantification", "成果量化", quantification, 15,
                entries.isEmpty() ? "暂无可量化的履历" : quantified + " / " + entries.size() + " 段履历包含数字"));
        if (!entries.isEmpty() && quantified == 0) {
            addIssue(issues, entries.get(0).sectionType(), "content", "medium", "描述缺少量化结果", "履历里没有数字，成果大小难以判断。", "补充规模、耗时、比例或业务结果；无法量化时写清影响范围。");
        }

        int format = 15;
        if (!email.isBlank() && !EMAIL.matcher(email).matches()) {
            format -= 2;
            addIssue(issues, "basic_info", "email", "low", "邮箱格式可能有误", "当前邮箱不符合常见格式。", "检查 @ 与域名拼写。");
        }
        if (!phone.isBlank() && phone.replaceAll("\\D", "").length() < 7) format -= 2;
        if (!skills.isBlank() && skills.length() < 10) format -= 1;
        if (entries.stream().anyMatch(e -> e.period().isBlank())) {
            format -= 2;
            addIssue(issues, entries.get(0).sectionType(), "period", "medium", "履历缺少时间", "没有起止时间的履历可信度会下降。", "补充开始与结束时间；仍在进行中的履历可勾选「至今」。");
        }
        format = Math.max(0, Math.min(15, format));
        dimensions.add(new Dimension("format", "格式规范", format, 15,
                format >= 13 ? "日期、联系方式和结构未发现明显问题" : "有少量格式细节需要检查"));

        if (self.isBlank()) addIssue(issues, "self_evaluation", "content", "low", "缺少自我评价", "一段 2-3 行的总结能帮助招聘方快速了解你。", "用 3 句话概括经验年限、擅长方向与求职动机。");
        if (skills.isBlank()) addIssue(issues, "skills_summary", "content", "medium", "没有填写个人技能", "技能关键词会影响简历筛选与搜索命中。", "列出与目标岗位相关的技术、工具和方法，不要堆砌无关技能。");
        if (!hasExtra) addIssue(issues, "portfolio", "title", "low", "缺少作品或技能证明", "作品集、技能熟练度或证书能让能力更具体。", "补充一个最能代表能力的作品或项目链接。");

        int total = completenessScore + jobMatch + expression + quantification + format;
        total = Math.max(0, Math.min(100, total));
        issues.sort((a, b) -> severityWeight(b.severity()) - severityWeight(a.severity()));
        if (issues.size() > 12) {
            issues = new ArrayList<>(issues.subList(0, 12));
        }
        return new Analysis(completeness, total, grade(total), dimensions, issues);
    }

    private void collectEntries(JsonNode section, String type, List<Entry> entries) {
        if (section == null) return;
        for (JsonNode item : section.path("items")) {
            JsonNode fields = item.path("fields");
            String content = plain(fields, "content");
            if (content.isBlank()) continue;
            String title = switch (type) {
                case SectionTypes.WORK -> text(fields, "company") + " · " + text(fields, "position");
                case SectionTypes.PROJECT -> text(fields, "name") + " · " + text(fields, "role");
                default -> text(fields, "name") + " · " + text(fields, "role");
            };
            entries.add(new Entry(type, title, content, periodText(fields.path("period"))));
        }
    }

    private boolean hasSchool(JsonNode section) {
        if (section == null) return false;
        for (JsonNode item : section.path("items")) {
            JsonNode fields = item.path("fields");
            if (!text(fields, "school").isBlank() && !text(fields, "major").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExtra(Map<String, JsonNode> visible) {
        JsonNode portfolio = visible.get(SectionTypes.PORTFOLIO);
        if (portfolio != null && portfolio.path("items").size() > 0) {
            for (JsonNode item : portfolio.path("items")) {
                if (!text(item.path("fields"), "title").isBlank()) return true;
            }
        }
        JsonNode proficiency = visible.get(SectionTypes.PROFICIENCY);
        if (proficiency != null && proficiency.path("items").size() >= 2) return true;
        for (String type : List.of(SectionTypes.AWARD, SectionTypes.CERTIFICATE, SectionTypes.COMPETITION)) {
            JsonNode section = visible.get(type);
            if (section != null && section.path("items").size() > 0) {
                for (JsonNode item : section.path("items")) {
                    if (!text(item.path("fields"), "name").isBlank()) return true;
                }
            }
        }
        return false;
    }

    private List<String> titleTokens(String jobTitle) {
        if (jobTitle.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String word : GENERIC_TITLE_WORDS) {
            if (jobTitle.contains(word)) tokens.add(word);
        }
        String stripped = jobTitle;
        for (String word : GENERIC_TITLE_WORDS) {
            stripped = stripped.replace(word, " ");
        }
        for (String part : stripped.split("[\\s/、，,·]+")) {
            if (part.length() >= 2) tokens.add(part);
        }
        if (tokens.isEmpty()) tokens.add(jobTitle);
        return tokens;
    }

    private void addIssue(List<Issue> issues, String sectionType, String fieldKey, String severity,
                          String title, String detail, String suggestion) {
        issues.add(new Issue("issue_" + (issues.size() + 1), sectionType, fieldKey, severity, title, detail, suggestion));
    }

    private int severityWeight(String severity) {
        return switch (severity) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private JsonNode fields(JsonNode section) {
        if (section == null) return json.object();
        JsonNode items = section.path("items");
        if (items.isArray() && items.size() > 0) {
            return items.get(0).path("fields");
        }
        return json.object();
    }

    private String text(JsonNode fields, String key) {
        return fields.path(key).asText("").trim();
    }

    private String plain(JsonNode fields, String key) {
        return Jsoup.parse(fields.path(key).asText("")).text().trim();
    }

    private String periodText(JsonNode period) {
        String start = period.path("start").asText("");
        String end = period.path("current").asBoolean(false) ? "至今" : period.path("end").asText("");
        return (start + " " + end).trim();
    }

    private String grade(int score) {
        if (score >= 90) return "优秀";
        if (score >= 75) return "良好";
        if (score >= 60) return "待提升";
        return "需打磨";
    }

    private record Entry(String sectionType, String title, String content, String period) {
    }

    public record Dimension(String key, String label, int score, int max, String summary) {
    }

    public record Issue(String id, String sectionType, String fieldKey, String severity,
                        String title, String detail, String suggestion) {
    }

    public record Analysis(int completeness, int totalScore, String grade,
                           List<Dimension> dimensions, List<Issue> issues) {
    }
}
