package cn.minims.who.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 修掉无头浏览器打印中文 PDF 时的一个字形反查缺陷。
 *
 * <p>Noto CJK 把「康熙部首」和对应的汉字共用同一个字形：⽅（U+2F45）与 方（U+65B9）是同一张轮廓。
 * Chrome 生成 ToUnicode 时按字形反查码点，取的是编号最小的那一个，于是 方 被写成 ⽅ 这类部首码点。
 * 页面上看不出任何差别，但复制文字和 ATS 解析拿到的就是部首字符，
 * 一份中文简历里能错掉 6%～7% 的汉字（方、人、日、月、用、大、一、工、部、门都在其中）。
 *
 * <p>这里只改 CMap 里的目标码点，内容流与字体一个字节都不动，所以版面与字形和改写前完全一致。
 * 兜不住的那些部首（U+2E80–U+2EFF 在 Unicode 里没有兼容分解）走
 * {@code RADICAL_TO_IDEOGRAPH} 表，表里的对应关系取自镜像实际安装的 NotoSansCJKsc，
 * 生成方式见 {@code docs/部署指南.md} 的字体说明。用微软雅黑、SimSun 这类
 * 「部首与汉字不共字形」的字体渲染时本来就查不到部首码点，这个方法原样返回。
 */
@Component
public class PdfToUnicodeNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PdfToUnicodeNormalizer.class);

    private static final Pattern BFCHAR_BLOCK = Pattern.compile("beginbfchar(.*?)endbfchar", Pattern.DOTALL);
    private static final Pattern BFRANGE_BLOCK = Pattern.compile("beginbfrange(.*?)endbfrange", Pattern.DOTALL);
    /** bfchar 的一条映射：源 CID 与目标（目标允许多码点，即 4 的整数倍个 hex）。 */
    private static final Pattern BFCHAR_ENTRY = Pattern.compile("<([0-9A-Fa-f]{2,8})>\\s*<([0-9A-Fa-f]{2,8})>");
    /** bfrange 的两种写法：<lo> <hi> <dst> 与 <lo> <hi> [ <dst1> <dst2> … ]。 */
    private static final Pattern BFRANGE_ENTRY = Pattern.compile(
            "<([0-9A-Fa-f]{2,8})>\\s*<([0-9A-Fa-f]{2,8})>\\s*(?:\\[\\s*([^\\]]*?)\\s*]|<([0-9A-Fa-f]{2,8})>)");

    /** CMap 规范要求每个 bfchar 块不超过 100 条。 */
    private static final int BLOCK_LIMIT = 100;
    /** 展开区间时最多接受多少条映射，超出就当这段结构不认识，保持原样。 */
    private static final int EXPAND_LIMIT = 2000;

    private static final Map<Integer, Integer> RADICAL_TO_IDEOGRAPH = radicalToIdeograph();

    private static Map<Integer, Integer> radicalToIdeograph() {
        int[][] pairs = {
            {0x2E82, 0x4E5B}, {0x2E83, 0x4E5A}, {0x2E85, 0x4EBB}, {0x2E89, 0x5202},
            {0x2E8B, 0x353E}, {0x2E8F, 0x5C23}, {0x2E90, 0x5C22}, {0x2E92, 0x5DF3},
            {0x2E93, 0x5E7A}, {0x2E94, 0x5F51}, {0x2E95, 0x5F50}, {0x2E96, 0x5FC4},
            {0x2E98, 0x624C}, {0x2E99, 0x6535}, {0x2E9B, 0x65E1}, {0x2E9E, 0x6B7A},
            {0x2EA0, 0x6C11}, {0x2EA1, 0x6C35}, {0x2EA3, 0x706C}, {0x2EA4, 0x722B},
            {0x2EA6, 0x4E2C}, {0x2EA8, 0x72AD}, {0x2EAD, 0x793B}, {0x2EAF, 0x7CF9},
            {0x2EB0, 0x7E9F}, {0x2EB1, 0x7F53}, {0x2EB3, 0x34C1}, {0x2EB9, 0x8002},
            {0x2EBA, 0x8080}, {0x2EBE, 0x8279}, {0x2EC2, 0x8864}, {0x2EC5, 0x89C1},
            {0x2EC6, 0x89D2}, {0x2EC8, 0x8BA0}, {0x2EC9, 0x8D1D}, {0x2ECB, 0x8F66},
            {0x2ECC, 0x8FB6}, {0x2ED0, 0x9485}, {0x2ED1, 0x9577}, {0x2ED2, 0x9578},
            {0x2ED3, 0x957F}, {0x2ED4, 0x95E8}, {0x2ED6, 0x961D}, {0x2ED8, 0x9752},
            {0x2ED9, 0x97E6}, {0x2EDA, 0x9875}, {0x2EDB, 0x98CE}, {0x2EDC, 0x98DE},
            {0x2EDD, 0x98DF}, {0x2EDF, 0x98E0}, {0x2EE0, 0x9963}, {0x2EE2, 0x9A6C},
            {0x2EE3, 0x9AA8}, {0x2EE4, 0x9B3C}, {0x2EE5, 0x9C7C}, {0x2EE6, 0x9E1F},
            {0x2EE7, 0x5364}, {0x2EE8, 0x9EA6}, {0x2EE9, 0x9EC4}, {0x2EEA, 0x9EFE},
            {0x2EEB, 0x6589}, {0x2EEC, 0x9F50}, {0x2EEE, 0x9F7F}, {0x2EEF, 0x7ADC},
            {0x2EF0, 0x9F99}, {0x2EF1, 0x9F9C}, {0x2EF2, 0x4E80}, {0x2F3E, 0x6237},
        };
        Map<Integer, Integer> map = new HashMap<>(pairs.length * 2);
        for (int[] pair : pairs) {
            map.put(pair[0], pair[1]);
        }
        return Map.copyOf(map);
    }

    /**
     * 尽力而为：不是 PDF、解析失败、结构不认识、写回失败，一律返回原始字节。
     * 文字保真度是加分项，不能因为它让导出整体失败。
     */
    public byte[] normalize(byte[] pdf) {
        if (pdf == null || pdf.length < 8 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            return pdf;
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int fixed = rewriteFonts(document);
            if (fixed == 0) {
                return pdf;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length);
            document.save(out);
            log.debug("PDF ToUnicode 归一化：修正 {} 个字体子集", fixed);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("PDF ToUnicode 归一化跳过：{}", e.toString());
            return pdf;
        }
    }

    private int rewriteFonts(PDDocument document) {
        int changed = 0;
        List<COSObject> fonts = document.getDocument().getObjectsByType(COSName.FONT);
        for (COSObject object : fonts) {
            COSBase base = object == null ? null : object.getObject();
            if (!(base instanceof COSDictionary font)) {
                continue;
            }
            if (!(font.getDictionaryObject(COSName.TO_UNICODE) instanceof COSStream toUnicode)) {
                continue;
            }
            if (rewriteCMapStream(toUnicode)) {
                changed++;
            }
        }
        return changed;
    }

    private boolean rewriteCMapStream(COSStream stream) {
        String source;
        try (InputStream input = stream.createInputStream()) {
            source = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return false;
        }
        if (!source.contains("begincmap")) {
            return false;
        }
        String rewritten = rewriteCMap(source);
        // 映射条数必须一条不少：解析没吃准就不写回，宁可在文字保真度上退回原样。
        int before = mappingCount(source);
        if (rewritten.equals(source) || before < 0 || mappingCount(rewritten) != before) {
            return false;
        }
        try (OutputStream output = stream.createOutputStream(COSName.FLATE_DECODE)) {
            output.write(rewritten.getBytes(StandardCharsets.ISO_8859_1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 逐段数出 CID 到码点的映射条数；有任何一段解析不了就返回 -1，调用方据此放弃改写。 */
    private static int mappingCount(String cmap) {
        int total = 0;
        Matcher bfchar = BFCHAR_BLOCK.matcher(cmap);
        while (bfchar.find()) {
            String body = bfchar.group(1);
            Matcher entries = BFCHAR_ENTRY.matcher(body);
            int consumed = 0;
            while (entries.find()) {
                if (!body.substring(consumed, entries.start()).isBlank()) {
                    return -1;
                }
                consumed = entries.end();
                total++;
            }
            if (!body.substring(consumed).isBlank()) {
                return -1;
            }
        }
        Matcher bfrange = BFRANGE_BLOCK.matcher(cmap);
        while (bfrange.find()) {
            List<String[]> entries = parseBfrange(bfrange.group(1));
            if (entries == null) {
                return -1;
            }
            total += entries.size();
        }
        return total;
    }

    /** 包级可见：用一段真实 CMap 文本即可验证，不需要起浏览器。 */
    static String rewriteCMap(String cmap) {
        if (!cmap.contains("beginbfchar") && !cmap.contains("beginbfrange")) {
            return cmap;
        }
        return rewriteBfrangeBlocks(rewriteBfcharBlocks(cmap));
    }

    /** bfchar 是逐条映射，直接换掉目标码点即可。 */
    private static String rewriteBfcharBlocks(String cmap) {
        Matcher blocks = BFCHAR_BLOCK.matcher(cmap);
        StringBuilder out = new StringBuilder(cmap.length());
        while (blocks.find()) {
            String body = blocks.group(1);
            Matcher entries = BFCHAR_ENTRY.matcher(body);
            StringBuffer buffer = new StringBuffer(body.length());
            while (entries.find()) {
                String destination = entries.group(2);
                String fixed = fixDestination(destination);
                if (fixed.equals(destination)) {
                    continue;
                }
                entries.appendReplacement(buffer,
                        Matcher.quoteReplacement("<" + entries.group(1) + "> <" + fixed + ">"));
            }
            entries.appendTail(buffer);
            blocks.appendReplacement(out, Matcher.quoteReplacement("beginbfchar" + buffer + "endbfchar"));
        }
        blocks.appendTail(out);
        return out.toString();
    }

    /**
     * bfrange 的目标是「起点 + 偏移」，改一个起点会连带改掉整段区间，
     * 所以这段里只要出现了部首码点，就按 CID 逐条展开成 bfchar 重新写回；
     * 展开前后的映射条数一致，{@code mappingCount} 那道校验仍然成立。
     */
    private static String rewriteBfrangeBlocks(String cmap) {
        Matcher blocks = BFRANGE_BLOCK.matcher(cmap);
        StringBuilder out = new StringBuilder(cmap.length());
        while (blocks.find()) {
            String original = blocks.group();
            List<String[]> entries = parseBfrange(blocks.group(1));
            boolean needsFix = entries != null
                    && entries.stream().anyMatch(entry -> isRadicalDestination(entry[1]));
            blocks.appendReplacement(out, Matcher.quoteReplacement(
                    needsFix ? bfcharBlocks(entries) : original));
        }
        blocks.appendTail(out);
        return out.toString();
    }

    /** 解析失败、条目没覆盖整段、展开过大时返回 null，交给调用方保持原样。 */
    private static List<String[]> parseBfrange(String body) {
        Matcher entries = BFRANGE_ENTRY.matcher(body);
        List<String[]> flat = new ArrayList<>();
        int consumedTo = 0;
        while (entries.find()) {
            if (!body.substring(consumedTo, entries.start()).isBlank()) {
                return null;
            }
            consumedTo = entries.end();
            int low = Integer.parseInt(entries.group(1), 16);
            int high = Integer.parseInt(entries.group(2), 16);
            int count = high - low + 1;
            if (count < 1 || flat.size() + count > EXPAND_LIMIT) {
                return null;
            }
            String sourceWidth = entries.group(1);
            String[] destinations;
            if (entries.group(3) != null) {
                destinations = entries.group(3).trim().split("\\s+");
                if (destinations.length != count) {
                    return null;
                }
            } else {
                destinations = rangeDestinations(entries.group(4), count);
                if (destinations == null) {
                    return null;
                }
            }
            for (int offset = 0; offset < count; offset++) {
                flat.add(new String[]{hex(low + offset, sourceWidth.length()), destinations[offset]});
            }
        }
        if (flat.isEmpty() || !body.substring(consumedTo).isBlank()) {
            return null;
        }
        return flat;
    }

    /** 区间起点的最后一个码点单位按偏移递增，与 CMap 规范一致。 */
    private static String[] rangeDestinations(String start, int count) {
        if (start.length() % 4 != 0) {
            return null;
        }
        int units = start.length() / 4;
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            StringBuilder hex = new StringBuilder(start.length());
            for (int unit = 0; unit < units; unit++) {
                int value = Integer.parseInt(start.substring(unit * 4, unit * 4 + 4), 16);
                if (unit == units - 1) {
                    value += i;
                    if (value > 0xFFFF) {
                        return null;
                    }
                }
                hex.append(hex(value, 4));
            }
            result[i] = hex.toString();
        }
        return result;
    }

    private static String bfcharBlocks(List<String[]> entries) {
        StringBuilder text = new StringBuilder(entries.size() * 14);
        for (int start = 0; start < entries.size(); start += BLOCK_LIMIT) {
            text.append("beginbfchar\n");
            for (int i = start; i < Math.min(start + BLOCK_LIMIT, entries.size()); i++) {
                text.append('<').append(entries.get(i)[0]).append("> <").append(fixDestination(entries.get(i)[1])).append(">\n");
            }
            text.append("endbfchar\n");
        }
        return text.toString();
    }

    private static boolean isRadicalDestination(String destinationHex) {
        return destinationHex.length() == 4 && inRadicalBlock(Integer.parseInt(destinationHex, 16));
    }

    private static boolean inRadicalBlock(int codePoint) {
        return codePoint >= 0x2E80 && codePoint <= 0x2FDF;
    }

    private static String fixDestination(String destinationHex) {
        if (destinationHex.length() != 4) {
            return destinationHex;
        }
        int codePoint = Integer.parseInt(destinationHex, 16);
        if (!inRadicalBlock(codePoint)) {
            return destinationHex;
        }
        Integer mapped = RADICAL_TO_IDEOGRAPH.get(codePoint);
        if (mapped != null) {
            return hex(mapped, 4);
        }
        String normalized = Normalizer.normalize(String.valueOf((char) codePoint), Normalizer.Form.NFKC);
        if (normalized.length() != 1 || normalized.charAt(0) < 0x3400) {
            return destinationHex;
        }
        return hex(normalized.charAt(0), 4);
    }

    private static String hex(int value, int width) {
        String text = Integer.toHexString(value).toUpperCase(Locale.ROOT);
        return text.length() >= width ? text : "0".repeat(width - text.length()) + text;
    }
}
