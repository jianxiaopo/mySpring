package com.xiaopo.spring.controller;

import com.xiaopo.spring.annotation.MyController;
import com.xiaopo.spring.annotation.MyRequestMapping;
import com.xiaopo.spring.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @Description
 * @Author jxp
 **/
@MyController
public class TestController {

    @MyRequestMapping("/hello")
    public void hello(HttpServletRequest request, HttpServletResponse response,
                      @MyRequestParam("name") String name) throws IOException {
        response.getWriter().write( "hello:"+name);
    }
}
