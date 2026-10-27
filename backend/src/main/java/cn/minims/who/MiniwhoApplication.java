package cn.minims.who;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务目前只有「到期注销清除」一条；它与 Sa-Token 的内存令牌一样默认单实例运行，
 * 多实例部署要先给清除任务加分布式锁，否则两个进程会抢着删同一批账号。
 */
@SpringBootApplication
@EnableScheduling
public class MiniwhoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniwhoApplication.class, args);
    }
}
