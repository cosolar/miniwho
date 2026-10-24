package cn.minims.who.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SectionTypes {

    private static final Pattern CUSTOM_INSTANCE = Pattern.compile("^custom_[1-5]$");

    public static final String BASIC = "basic_info";
    public static final String INTENTION = "job_intention";
    public static final String EDUCATION = "education";
    public static final String CAMPUS = "campus_experience";
    public static final String WORK = "work_experience";
    public static final String PROJECT = "project_experience";
    public static final String SKILLS = "skills_summary";
    public static final String SELF = "self_evaluation";
    public static final String PAPER = "paper_publication";
    public static final String PORTFOLIO = "portfolio";
    public static final String PROFICIENCY = "skill_proficiency";
    public static final String POSTGRADUATE = "postgraduate_info";
    public static final String COMPETITION = "competition";
    public static final String AWARD = "award";
    public static final String CERTIFICATE = "certificate";
    public static final String JOURNAL = "journal_paper";
    public static final String HOBBY = "hobby";
    public static final String CUSTOM = "custom";

    public static final List<String> CORE_ORDER = List.of(
            BASIC, INTENTION, EDUCATION, WORK, PROJECT, CAMPUS,
            SKILLS, PROFICIENCY, PAPER, PORTFOLIO, SELF
    );

    public static final List<String> OPTIONAL_TYPES = List.of(
            POSTGRADUATE, COMPETITION, AWARD, CERTIFICATE, JOURNAL, HOBBY, CUSTOM
    );

    public static final Set<String> ALL = Set.of(
            BASIC, INTENTION, EDUCATION, CAMPUS, WORK, PROJECT, SKILLS, SELF,
            PAPER, PORTFOLIO, PROFICIENCY, POSTGRADUATE, COMPETITION, AWARD,
            CERTIFICATE, JOURNAL, HOBBY, CUSTOM
    );

    public static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry(BASIC, "基本信息"),
            Map.entry(INTENTION, "求职意向"),
            Map.entry(EDUCATION, "教育背景"),
            Map.entry(CAMPUS, "在校经历"),
            Map.entry(WORK, "工作经历"),
            Map.entry(PROJECT, "项目经历"),
            Map.entry(SKILLS, "个人技能"),
            Map.entry(SELF, "自我评价"),
            Map.entry(PAPER, "论文发表"),
            Map.entry(PORTFOLIO, "作品集"),
            Map.entry(PROFICIENCY, "技能熟练度"),
            Map.entry(POSTGRADUATE, "考研信息"),
            Map.entry(COMPETITION, "竞赛经历"),
            Map.entry(AWARD, "获奖情况"),
            Map.entry(CERTIFICATE, "资格证书"),
            Map.entry(JOURNAL, "论文期刊"),
            Map.entry(HOBBY, "兴趣爱好"),
            Map.entry(CUSTOM, "自定义模块")
    );

    public static final Set<String> SINGLE_ITEM_TYPES = Set.of(
            BASIC, INTENTION, SKILLS, SELF, PAPER, POSTGRADUATE, HOBBY
    );

    public static final Set<String> UNHIDEABLE = Set.of(BASIC, EDUCATION);

    public static final Set<String> RICH_KEYS = Set.of("content", "description", "note", "summary");

    private SectionTypes() {
    }

    public static boolean isCustom(String type) {
        return CUSTOM.equals(type) || (type != null && CUSTOM_INSTANCE.matcher(type).matches());
    }

    public static boolean isValid(String type) {
        return ALL.contains(type) || isCustom(type);
    }

    public static String titleOf(String type) {
        return TITLES.getOrDefault(type, TITLES.get(CUSTOM));
    }

    public static boolean isOptional(String type) {
        return OPTIONAL_TYPES.contains(type) || isCustom(type);
    }
}
