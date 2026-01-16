package com.nowcoder.community;

import com.nowcoder.community.dao.AlphaDao;
import com.nowcoder.community.service.AlphaService;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
class CommunityApplicationTests implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    @Autowired
    @Qualifier("Hibernate")
    private AlphaDao alphaDao;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

    }

    @Test
    public void testApplicationContext() {
        System.out.println(this.applicationContext);
    }

    @Test
    public void testDao() {
        System.out.println(this.applicationContext);

        AlphaDao alphaDao = applicationContext.getBean(AlphaDao.class);
        System.out.println(alphaDao.select());

        alphaDao = applicationContext.getBean("Hibernate", AlphaDao.class);
        System.out.println(alphaDao.select());
    }

    @Test
    public void testService() {
        AlphaService alphaService = this.applicationContext.getBean(AlphaService.class);
        System.out.println(alphaService);
        // why destroy not print
    }

    @Test
    public void testBeanConfig() {
        SimpleDateFormat simpleDateFormat = this.applicationContext.getBean(SimpleDateFormat.class);
        System.out.println(simpleDateFormat.format(new Date()));
    }

    @Test
    public void testDI() {
        System.out.println(this.alphaDao);
    }

}
