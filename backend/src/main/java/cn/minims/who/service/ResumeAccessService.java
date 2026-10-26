package cn.minims.who.service;

import cn.minims.who.common.BizException;
import cn.minims.who.entity.Resume;
import cn.minims.who.mapper.ResumeMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 简历归属校验的唯一入口。
 * 头像、条目图片、PDF 导出等多个服务都要「按当前用户取简历」，
 * 集中在这里可以避免每个服务各写一份判断，也避免它们互相依赖成环。
 */
@Service
public class ResumeAccessService {

    private final ResumeMapper resumeMapper;

    public ResumeAccessService(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }

    /** 取当前用户名下的简历；不存在与不属于当前用户统一按「不存在」处理，不泄露他人简历是否存在。 */
    public Resume findOwned(long userId, long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !Objects.equals(resume.getUserId(), userId)) {
            throw BizException.notFound("简历不存在或无权访问");
        }
        return resume;
    }

    /** 公开签名通道只校验简历是否存在，鉴权由签名 token 负责。 */
    public Resume requireExisting(long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw BizException.notFound("简历不存在");
        }
        return resume;
    }
}
