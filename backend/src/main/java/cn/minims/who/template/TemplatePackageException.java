package cn.minims.who.template;

import java.util.List;

/**
 * 模板包不合法时一次性带齐全部问题，导入接口要把原因整份回给作者，而不是挤在一句话里。
 */
public class TemplatePackageException extends RuntimeException {

    private final List<String> problems;

    public TemplatePackageException(List<String> problems) {
        super(String.join("；", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
