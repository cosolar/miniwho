package cn.minims.who.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.minims.who.content.ScoreEngine;
import cn.minims.who.dto.ResumeDtos.ReportVo;
import cn.minims.who.entity.Resume;
import cn.minims.who.entity.ResumeReport;
import cn.minims.who.mapper.ResumeMapper;
import cn.minims.who.mapper.ResumeReportMapper;
import cn.minims.who.util.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {

    /** 每份简历只保留最近 10 份报告，诊断是高频操作，表不能只增不删。 */
    private static final int KEEP_PER_RESUME = 10;

    private final ResumeService resumeService;
    private final ResumeMapper resumeMapper;
    private final ResumeReportMapper reportMapper;
    private final ScoreEngine scoreEngine;
    private final Json json;

    public ReportService(ResumeService resumeService, ResumeMapper resumeMapper,
                         ResumeReportMapper reportMapper, ScoreEngine scoreEngine, Json json) {
        this.resumeService = resumeService;
        this.resumeMapper = resumeMapper;
        this.reportMapper = reportMapper;
        this.scoreEngine = scoreEngine;
        this.json = json;
    }

    @Transactional
    public ReportVo generate(long userId, long resumeId) {
        Resume resume = resumeService.findOwned(userId, resumeId);
        ScoreEngine.Analysis analysis = scoreEngine.analyze(json.read(resume.getContent()));
        ResumeReport report = new ResumeReport();
        report.setResumeId(resumeId);
        report.setUserId(userId);
        report.setContentVersion(resume.getContentVersion());
        report.setTotalScore(analysis.totalScore());
        report.setGrade(analysis.grade());
        report.setDimensions(json.write(json.mapper().valueToTree(analysis.dimensions())));
        report.setIssues(json.write(json.mapper().valueToTree(analysis.issues())));
        reportMapper.insert(report);
        pruneOldReports(resumeId);

        long now = System.currentTimeMillis();
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .set(Resume::getScore, analysis.totalScore())
                .set(Resume::getCompleteness, analysis.completeness())
                .set(Resume::getScoreUpdatedAt, now));
        return toVo(report, true);
    }

    public ReportVo latest(long userId, long resumeId) {
        Resume resume = resumeService.findOwned(userId, resumeId);
        ResumeReport report = reportMapper.selectOne(new LambdaQueryWrapper<ResumeReport>()
                .eq(ResumeReport::getResumeId, resumeId)
                .orderByDesc(ResumeReport::getCreatedAt)
                .last("limit 1"));
        if (report == null) {
            return null;
        }
        return toVo(report, report.getContentVersion().equals(resume.getContentVersion()));
    }

    private void pruneOldReports(long resumeId) {
        List<ResumeReport> keep = reportMapper.selectList(new LambdaQueryWrapper<ResumeReport>()
                .eq(ResumeReport::getResumeId, resumeId)
                .orderByDesc(ResumeReport::getId)
                .last("limit " + KEEP_PER_RESUME));
        if (keep.size() < KEEP_PER_RESUME) {
            return;
        }
        reportMapper.delete(new LambdaQueryWrapper<ResumeReport>()
                .eq(ResumeReport::getResumeId, resumeId)
                .lt(ResumeReport::getId, keep.get(keep.size() - 1).getId()));
    }

    private ReportVo toVo(ResumeReport report, boolean current) {
        return new ReportVo(report.getId(), report.getResumeId(), report.getContentVersion(),
                report.getTotalScore(), report.getGrade(), json.read(report.getDimensions()),
                json.read(report.getIssues()), report.getCreatedAt(), current);
    }
}
