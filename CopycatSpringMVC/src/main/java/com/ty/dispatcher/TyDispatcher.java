package com.ty.dispatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ty.annotation.TyController;
import com.ty.annotation.TyRequestMapping;
import com.ty.annotation.TyRequestParam;
/**
 * 此类的主要作用为初始化相关配置以及分发请求
 * 首先要将此servlet配置在web.xml中，在容器启动的时候，TyDispatcher初始化，并且在整个容器的生命周期中，只会创建一个TyDispatcher实例
 * TyDispatcher在web.xml中的配置信息将会包装成ServletConfig类
 */
public class TyDispatcher extends HttpServlet {
private static final long serialVersionUID = 9003485862460547072L;
    
    //自动扫描的basePackage
    private Properties property = new Properties();
    
    //保存所有需要自动创建实例对象的类的名字
    private List<String> classNameList = new ArrayList<>();
    
    //IOC容器，是一个键值对(数据格式:{"TestController": Object})
    public Map<String, Object> ioc = new HashMap<>();
    
    /*
     * 用于存放url与method对象映射关系的容器,数据格式为{"/base/TySpringMVCTest": Method}
     */    
    private Map<String, Method> handlerMappingMap = new HashMap<>();
    
    //用于将请求url与controller实例对象建立映射关系(数据格式:{"": Controller})
    public Map<String, Object> controllerContainer = new HashMap<>();
    
    /*
     * init方法主要用于初始化相关配置，比如basePackage
     * 
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        //配置basePackage，该包下所有的controller全部自动创建实例

        initBaseScan(config);
        
        //扫描basePackage下所有应该被实例化的类
        scanBasePackage(property.getProperty("scanPackage"));
        
        //根据classNameList中的文件去创建实例对象(即实例化basePackage下的controller)
        createInstance(classNameList);
        
        //初始化handlerMapping
        initHandlerMapping(ioc);
    }

    private void initBaseScan(ServletConfig config) {
        /*
         * servlet会将web.xml中的配置封装成ServletConfig对象。
         * 从该对象获取<init-param>所配置的初始化参数
         */        
        String location = config.getInitParameter("contextConfigLocation");
        //通过类加载器去读取该文件中的数据，并封装成流，然后通过property去加载流
        InputStream input = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            property.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void scanBasePackage(String basePackage) {
        /*
         * 找到basePackage下所有的resource(resource即为图片、声音、文本等等数据)
         * 另外需要将com.ty.controller转换成/com/ty/controller/这种相对路径形式
         * \\.是为了防止转义
         */
        URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        //此时file相当于一个总文件夹
        File totalFolder = new File(url.getFile());
        /*
         * 列出总文件夹下的文件夹以及文件
         */
        for(File file: totalFolder.listFiles()) {
            if(file.isDirectory()) {
                //通过递归去找到最后一层级的文件，其实也就是.class文件(编译后)
                scanBasePackage(basePackage + file.getName());
            } else {
                //要将编译后文件的.class后缀去掉
                classNameList.add(basePackage + "." + file.getName().replaceAll(".class", ""));
            } 
        }
    }
    
    private void createInstance(List<String> classNameList) {
        if(classNameList == null || classNameList.size() == 0) {
            return;
        }
        
        for(String className: classNameList) {
            try {
                //根据className获取Class对象，通过反射去实例化对象
                Class<?> clazz = Class.forName(className);
                
                //只需要将basePackage下的controller实例化即可
                if(clazz.isAnnotationPresent(TyController.class)) {
                    Object obj = clazz.newInstance();
                    //这点跟spring容器稍有不同的是就是key值虽为类名，但是首字母并没有小写
                    ioc.put(clazz.getSimpleName(), obj);
                }                
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
    
    /*
     * controller现已实例化，但是还要对其方法的url(controller上注解的url+方法上的url)与方法对象建立起映射关系
     * ioc数据格式:{"TestController": Object}
     */
    private void initHandlerMapping(Map<String, Object> ioc) {
        if(ioc.isEmpty()) {
            return;
        }
        
        for(Entry<String, Object> entry: ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            //如果ioc容器中的对象不是controller对象，不进行处理
            if(!clazz.isAnnotationPresent(TyController.class)) {
                continue;
            }
            
            /*
             * controller上配置的@TyRequestMapping的值。因为controller类上使用@TyRequestMapping注解
             * 是类维度的，所以通过clazz.getAnnotation获取value值
             */            
            String baseURL = clazz.getAnnotation(TyController.class).value();
            
            //通过clazz获取到该类下所有的方法数组
            Method[] methods = clazz.getMethods();
            for(Method method: methods) {
                //判断Method对象上是否存在@TyRequestMapping的注解，若有，取其value值
                if(!method.isAnnotationPresent(TyRequestMapping.class)) {
                    continue;
                }
                
                String methodURL =  method.getAnnotation(TyRequestMapping.class).value();
                //数据格式:{"/controller/methodURL": Method}
                handlerMappingMap.put(baseURL + methodURL, method);
                //并且需要将url对应controller的映射关系保存
                controllerContainer.put(baseURL + methodURL, entry.getValue());
            }
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    /*
     * 此类用于处理客户端的请求，所有核心的分发逻辑由doPost控制，包括解析客户端请求参数等等
     * 
     */
    @SuppressWarnings("unchecked")
	@Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws 
    ServletException, IOException {
        /*
         * 根据JDK的说明，若请求url为http://localhost:8080/TySpringMVC/controller/testMethod
         * 则requestURL为/TySpringMVC/controller/testMethod。所以应该将项目名去掉
         */
        String requestURL = req.getRequestURI();
        //contextPath的路径为/TySpringMVC
        String contextPath = req.getContextPath();
        //requestMappingURL为/controller/testMethod
        String requestMappingURL = requestURL.replaceAll(contextPath, "");
        Method method = handlerMappingMap.get(requestMappingURL);
        
        if(method == null) {
            /*
             * 通过Writer.write向与客户端连接的I/O通道写数据。并且这地方注意设置编码，要保证客户端解析编码与
             * 代码中编码格式一致。这也是出现乱码的根本原因所在
             */
            resp.setCharacterEncoding("UTF-8");
            String errorMessage = "404 请求URL不存在！";
            resp.getWriter().write(errorMessage);
            return;
        }
        
        /*
         * 获取前端入参，例如url为http://localhost:8080/TySpringMVC/sss?name=ty
         * map:{"name": ["ty","\s"}
         */
        Map<String, String[]> paramMap = req.getParameterMap();

        //获取该method的所有参数对象数组。注：这地方找了好久api，才找到此方法。。。
        Parameter[] params = method.getParameters();
        //用于将前端参数封装，并且供method执行
        Object[] paramArr = new Object[params.length];
        for(int i = 0; i < params.length; i++) {
            //这两个if是用于解决method中的参数类型为HttpServletRequest或HttpServletResponse
            if("HttpServletRequest".equals(params[i].getType().getSimpleName())) {
                paramArr[i] = req;
                continue;
            }
                
            if("HttpServletResponse".equals(params[i].getType().getSimpleName())) {
                paramArr[i] = resp;
                continue;
            }
                
            if(params[i].isAnnotationPresent(TyRequestParam.class)) {
                String paramKey = params[i].getAnnotation(TyRequestParam.class).value();
                //客户端传过来的参数会是[小可爱]这种形式，因此需要去除[以及]，并且需要使用转义符
                String value = Arrays.toString(paramMap.get(paramKey)).replaceAll("\\[", "").replaceAll("\\]", "");
                paramArr[i] = value;
            }    
        }
        
        //开始调用反射来执行method
        try {
            method.invoke(controllerContainer.get(requestMappingURL), paramArr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
