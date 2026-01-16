package com.nowcoder.community.controller;

import com.nowcoder.community.service.AlphaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;

@Controller
@RequestMapping("/feng")
public class AlphaController {

    @Autowired
    private AlphaService alphaService;

    @RequestMapping("/yun")
    @ResponseBody
    public String sayHello(HttpServletRequest requests, HttpServletResponse response) {
        return "hello fengyun !";
    }


    @RequestMapping("/http")
    public void http(HttpServletRequest requests, HttpServletResponse response) {
        // request
        System.out.println(requests.getMethod());
        System.out.println(requests.getServletPath());
        Enumeration<String> enumeration = requests.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement();
            String value = requests.getHeader(name);
            System.out.println(name + " : " + value);
        }
        System.out.println(requests.getParameter("code"));

        //response
        response.setContentType("text/html;charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write("<h1>牛客网</h1>");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
