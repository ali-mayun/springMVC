package com.ty.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ty.annotation.TyController;
import com.ty.annotation.TyRequestMapping;
import com.ty.annotation.TyRequestParam;

@TyController("/baseController")
public class BaseCntroller {
	@TyRequestMapping("/firstMethod")
    public void firstMethod(HttpServletRequest req, HttpServletResponse resp, @TyRequestParam("name") String name) {
        try {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("我不管，我最帅，我是你们的" + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
