package cn.minims.who.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出的中文 PDF 里，浏览器按字形反查码点时会把 方 这类常用汉字写成康熙部首（⽅）。
 * 改写只碰 ToUnicode，所以测试盯两件事：目标码点换回汉字，映射条数与其余内容一条不少。
 */
class PdfToUnicodeNormalizerTest {

    private final PdfToUnicodeNormalizer normalizer = new PdfToUnicodeNormalizer();

    @Test
    void replacesKangxiRadicalDestinationsInBfchar() {
        String cmap = """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                3 beginbfchar
                <0A> <2F45>
                <36> <2F47>
                <3E> <65ED>
                endbfchar
                endcmap
                """;
        String fixed = PdfToUnicodeNormalizer.rewriteCMap(cmap);
        assertThat(fixed).contains("<0A> <65B9>").contains("<36> <65E5>").contains("<3E> <65ED>");
        assertThat(fixed).doesNotContain("2F45").doesNotContain("2F47");
    }

    @Test
    void usesTheFontTableWhenNfkcWouldPickTheTraditionalForm() {
        // ⼾：NFKC 给的是繁体 戶，而这份字体里与它同字形的是简体 户。
        String cmap = "beginbfchar\n<12> <2F3E>\nendbfchar";
        assertThat(PdfToUnicodeNormalizer.rewriteCMap(cmap)).isEqualTo("beginbfchar\n<12> <6237>\nendbfchar");
        // 部首补充段在 Unicode 里没有兼容分解，只能靠表：⻓ -> 长、⺠ -> 民。
        assertThat(PdfToUnicodeNormalizer.rewriteCMap("beginbfchar\n<01> <2ED3>\n<02> <2EA0>\nendbfchar"))
                .contains("<01> <957F>").contains("<02> <6C11>");
    }

    @Test
    void expandsBfrangeIntoBfcharWhenItCarriesARadical() {
        String cmap = """
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                1 beginbfrange
                <0E> <11> <2F44>
                endbfrange
                """;
        String fixed = PdfToUnicodeNormalizer.rewriteCMap(cmap);
        assertThat(fixed).doesNotContain("beginbfrange");
        assertThat(fixed).contains("<0E> <65A4>")      // ⽄ -> 斤
                .contains("<0F> <65B9>")               // ⽅ -> 方
                .contains("<10> <65E0>")               // ⽆ -> 无
                .contains("<11> <65E5>");              // ⽇ -> 日
    }

    @Test
    void leavesPlainBfrangeAndNonPdfInputAlone() {
        String cmap = "1 beginbfrange\n<09> <0A> <0028>\nendbfrange";
        assertThat(PdfToUnicodeNormalizer.rewriteCMap(cmap)).isEqualTo(cmap);
        byte[] notPdf = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(normalizer.normalize(notPdf)).isSameAs(notPdf);
        assertThat(normalizer.normalize(null)).isNull();
    }

    @Test
    void keepsEveryMappingWhenRewritingARadicalCarryingPdf() throws IOException {
        byte[] original = samplePdf("""
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                /CMapName /Adobe-Identity-UCS def
                /CMapType 2 def
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                3 beginbfchar
                <0A> <2F45>
                <36> <2F47>
                <3E> <65ED>
                endbfchar
                1 beginbfrange
                <40> <41> <2ED3>
                endbfrange
                endcmap
                """);
        assertThat(toUnicodeMaps(original)).hasSize(1);
        byte[] fixed = normalizer.normalize(original);
        assertThat(fixed).isNotEqualTo(original);

        List<String> maps = toUnicodeMaps(fixed);
        assertThat(maps).hasSize(1);
        String cmap = maps.get(0);
        assertThat(cmap).contains("<0A> <65B9>").contains("<36> <65E5>").contains("<3E> <65ED>")
                .contains("<40> <957F>").contains("<41> <95E8>");
        assertThat(cmap).doesNotContain("<2F45>").doesNotContain("<2ED3");
        // 3 条 bfchar 原地改写 + 1 段 bfrange 展开成 2 条，映射总数不能变。
        assertThat(destinationCount(cmap)).isEqualTo(destinationCount(toUnicodeMaps(original).get(0)));
        assertThat(bfcharLines(cmap)).isEqualTo(5);
    }

    /** 真机对照：浏览器打印出来的整份简历，部首要一个不剩，映射条数与字体数量不能变。 */
    @Test
    void normalizesARealHeadlessChromeExport() throws IOException {
        Path sample = Path.of("..", ".tmp", "pdfcheck", "sample12", "jinshi.pdf");
        Assumptions.assumeTrue(Files.exists(sample), "本地导出样例缺失，跳过真机对照");
        byte[] original = Files.readAllBytes(sample);
        byte[] fixed = normalizer.normalize(original);
        assertThat(fixed).isNotEqualTo(original);
        List<String> before = toUnicodeMaps(original);
        List<String> after = toUnicodeMaps(fixed);
        assertThat(after).hasSize(before.size());
        assertThat(radicalDestinations(before)).isGreaterThan(0);
        assertThat(radicalDestinations(after)).isZero();
        List<Integer> beforeCounts = before.stream().map(PdfToUnicodeNormalizerTest::destinationCount).toList();
        List<Integer> afterCounts = after.stream().map(PdfToUnicodeNormalizerTest::destinationCount).toList();
        assertThat(afterCounts).isEqualTo(beforeCounts);
        assertThat(afterCounts).isNotEmpty();
        // 已经改写过的 PDF 再过一遍，应当原样返回，不会留下第二次改写的痕迹。
        assertThat(normalizer.normalize(fixed)).isSameAs(fixed);
    }

    private static long radicalDestinations(List<String> maps) {
        Pattern radical = Pattern.compile("<2[E-F][0-9A-Fa-f]{2}>");
        return maps.stream().mapToLong(map -> radical.matcher(map).results().count()).sum();
    }

    /** 把这份 PDF 里所有字体的 ToUnicode 文本读出来，走 PDF 结构而不是内部实现。 */
    private static List<String> toUnicodeMaps(byte[] pdf) throws IOException {
        List<String> maps = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (COSObject object : document.getDocument().getObjectsByType(COSName.FONT)) {
                if (!(object.getObject() instanceof COSDictionary font)) {
                    continue;
                }
                if (!(font.getDictionaryObject(COSName.TO_UNICODE) instanceof COSStream stream)) {
                    continue;
                }
                try (InputStream input = stream.createInputStream()) {
                    maps.add(new String(input.readAllBytes(), StandardCharsets.ISO_8859_1));
                }
            }
        }
        return maps;
    }

    /** 数一数 CMap 里的映射条数：bfchar 逐条 + bfrange 按区间长度展开。 */
    private static int destinationCount(String cmap) {
        int total = 0;
        for (String line : cmap.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.matches("<[0-9A-Fa-f]{2,8}>\\s*<[^>]*>")) {
                total++;
            } else if (trimmed.matches("<[0-9A-Fa-f]{2,8}>\\s*<[0-9A-Fa-f]{2,8}>\\s*<[^>]*>")) {
                total += rangeSize(trimmed);
            }
        }
        return total;
    }

    private static int bfcharLines(String cmap) {
        int inside = 0;
        int lines = 0;
        for (String line : cmap.lines().toList()) {
            String trimmed = line.trim();
            // 块的开头带条数前缀，形如 “3 beginbfchar”。
            if (trimmed.endsWith("beginbfchar")) {
                inside++;
            } else if (trimmed.equals("endbfchar")) {
                inside--;
            } else if (inside > 0 && trimmed.matches("<[0-9A-Fa-f]{2,8}>\\s*<[^>]*>")) {
                lines++;
            }
        }
        return lines;
    }

    private static int rangeSize(String entry) {
        String[] hexes = entry.replaceAll("[<>]", " ").trim().split("\\s+");
        return Integer.parseInt(hexes[1], 16) - Integer.parseInt(hexes[0], 16) + 1;
    }

    /** 手写一份最小可用的 PDF：只有一个页面和一把带 ToUnicode 的 Type0 字体。 */
    private static byte[] samplePdf(String cmap) throws IOException {
        String fontDescriptor = """
                << /Type /FontDescriptor /FontName /AAAAAA+NotoSansCJKsc-Regular /Flags 4
                /FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 880 /Descent -120
                /CapHeight 732 /StemV 80 /CIDToGIDMap /Identity >>
                """;
        String cidFont = """
                << /Type /Font /Subtype /CIDFontType2 /BaseFont /AAAAAA+NotoSansCJKsc-Regular
                /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>
                /FontDescriptor 7 0 R /DW 1000 >>
                """;
        String type0Font = """
                << /Type /Font /Subtype /Type0 /BaseFont /AAAAAA+NotoSansCJKsc-Regular
                /Encoding /Identity-H /DescendantFonts [5 0 R] /ToUnicode 6 0 R >>
                """;
        String stream = "<< /Length " + cmap.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n"
                + cmap + "endstream";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 4 0 R >> >> >>",
                type0Font,
                cidFont,
                stream,
                fontDescriptor);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.write(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        int start = out.size();
        StringBuilder xref = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            xref.append(String.format("%010d 00000 n \n", offset));
        }
        xref.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n").append(start).append("\n%%EOF\n");
        out.write(xref.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }
}
