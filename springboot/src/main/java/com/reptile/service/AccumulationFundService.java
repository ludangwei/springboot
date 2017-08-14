package com.reptile.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUtils;

import net.sf.json.JSONObject;

import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlImageInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.reptile.model.AccumulationFund;
import com.reptile.model.AccumulationFundInfo;
import com.reptile.model.FormBean;
import com.reptile.springboot.Scheduler;
import com.reptile.util.CrawlerUtil;
import com.reptile.util.Resttemplate;

/**
 * Created by HotWong on 2017/5/2 0002.
 */
@Service("accumulationFundService")
public class AccumulationFundService {
    private final static String detailsUrl="http://query.xazfgjj.gov.cn/gjjcx_gjjmxcx.jsp?urltype=tree.TreeTempUrl&wbtreeid=1177";
    private final static String infoUrl="http://query.xazfgjj.gov.cn/gjjcx_gjjxxcx.jsp?urltype=tree.TreeTempUrl&wbtreeid=1178";
    private final static String loginUrl="http://query.xazfgjj.gov.cn/index.jsp?urltype=tree.TreeTempUrl&wbtreeid=1172";
    private final static String verifyCodeImageUrl="http://query.xazfgjj.gov.cn/system/resource/creategjjcheckimg.jsp?randomid="+System.currentTimeMillis();
    private static CrawlerUtil crawlerutil=new CrawlerUtil();
    
    public Map<String,Object> login(FormBean bean, HttpServletRequest request){
        Map<String,Object> map=new HashMap<String,Object>();
        Map<String,Object> data=new HashMap<String,Object>();
        try {
            if(!bean.verifyParams(bean)){
                map.put("ResultInfo","提交数据有误,请刷新页面后重新输入!");
                map.put("ResultCode","0001");
                map.put("data",data);
                return map;
            }
            HttpSession session = request.getSession();
            Object sessionWebClient = session.getAttribute("sessionWebClient");
            Object sessionLoginPage = session.getAttribute("sessionLoginPage");
            if(sessionWebClient!=null && sessionLoginPage!=null){
                final WebClient webClient = (WebClient) sessionWebClient;
                final HtmlPage loginPage = (HtmlPage) sessionLoginPage;
                HtmlForm form = loginPage.getForms().get(0);
                form.getInputByName("csrftoken").setValueAttribute("40507");
                form.getInputByName("wbidcard").setValueAttribute(bean.getUserId().toString());
                form.getInputByName("cxydmc").setValueAttribute("当前年度");
                form.getInputByName("flag").setValueAttribute("login");
                form.getInputByName("wbzhigongname").setValueAttribute(bean.getUserName());
                form.getInputByName("wbrealmima").setValueAttribute(bean.getUserPass());
                form.getInputByName("wbmima").setValueAttribute(bean.getUserPass());
                form.getInputByName("surveyyanzheng").setValueAttribute(bean.getVerifyCode());
                HtmlImageInput submit = (HtmlImageInput)loginPage.getByXPath("//input[@type='image']").get(0);
                HtmlPage index=(HtmlPage)submit.click();
                String str=index.asText();
                if(str.indexOf("身份证号码：")!=-1){
                    map.put("ResultInfo","登录失败，请核对帐号密码和验证码!");
                    map.put("ResultCode","0001");
                    map.put("data",data);
                    return map;
                }
                HtmlPage detailsPage = webClient.getPage(detailsUrl);
                HtmlPage infoPage = webClient.getPage(infoUrl);
                HtmlTable infoTable=(HtmlTable)infoPage.getElementsByTagName("form").get(0).getElementsByTagName("table").get(0);
                AccumulationFundInfo info=new AccumulationFundInfo();
                info.setMonthBase(infoTable.getCellAt(3,3).asText());
                info.setUnitAccount(infoTable.getCellAt(4,1).asText());
                info.setUnitName(infoTable.getCellAt(5,1).asText());
                info.setOpeningDate(infoTable.getCellAt(7,1).asText());
                info.setCurrentState(infoTable.getCellAt(7,3).asText());
                info.setLastYearBalance(infoTable.getCellAt(9,1).asText());
                info.setPaidThisYear(infoTable.getCellAt(9,3).asText());
                info.setExternalTransfer(infoTable.getCellAt(10,1).asText());
                info.setExtractionThisYear(infoTable.getCellAt(10,3).asText());
                info.setBalance(infoTable.getCellAt(11,3).asText());
                List<AccumulationFund> detailsList = new ArrayList<AccumulationFund>();
                DomNodeList<HtmlElement> detils=detailsPage.getElementsByTagName("form").get(0).getElementsByTagName("tr");
                for(int i=3;i<detils.size();i++){
                    DomNodeList<HtmlElement> tdList=detils.get(i).getElementsByTagName("td");
                    AccumulationFund temp = new AccumulationFund();
                    for(int j=0;j<tdList.size();j++){
                        String key=detils.get(2).getElementsByTagName("td").get(j).asText();
                        String value=detils.get(i).getElementsByTagName("td").get(j).asText();
                        value=value.equals("")?null:value;
                        if(key.equals("收入")){
                            temp.setIncome(value);
                        }else if(key.equals("摘要")){
                            temp.setDesc(value);
                        }else if(key.equals("日期")){
                            temp.setCreateTime(value);
                        }else if(key.equals("支出")){
                            temp.setExpenditure(value);
                        }
                    }
                    detailsList.add(temp);
                }
                webClient.close();
                map.put("ResultInfo","查询成功");
                map.put("ResultCode","0000");
                data.put("info",info);
                data.put("detailsList",detailsList);
                SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
                map.put("queryDate",sdf.format(new Date()).toString());
                map.put("userId",bean.getUserId().toString());
                map.put("userName",bean.getUserName().toString());
                map.put("userPass",bean.getUserPass().toString());
                map.put("data",data);
//                HttpUtils.sendPost("http://192.168.3.16:8089/HSDC/person/accumulationFund", JSONObject.fromObject(map).toString());
                
                //ludangwei 2017-08-11
                Resttemplate resttemplate = new Resttemplate();
                map=resttemplate.SendMessageCredit(JSONObject.fromObject(map), "http://192.168.3.4:8081/HSDC/person/accumulationFund");
                //ludangwei 2017/08/10
                session.removeAttribute("sessionWebClient");
                session.removeAttribute("sessionLoginPage");
            }else{
                throw new Exception("服务器繁忙，请刷新页面后重试!");
            }
        } catch (Exception e) {
            map.clear();
            data.clear();
            map.put("ResultInfo","服务器繁忙，请刷新页面后重试!");
            map.put("ResultCode","0002");
        }
        map.put("data",data);
        
        return map;
    }

    public Map<String,Object> getVerifyImage(HttpServletResponse response, HttpServletRequest request){
        Map<String,Object> data=new HashMap<String,Object>();
        Map<String,Object> map=new HashMap<String,Object>();
        try {
            HttpSession session = request.getSession();
            Object sessionWebClient = session.getAttribute("sessionWebClient");
            Object sessionLoginPage = session.getAttribute("sessionLoginPage");
            String verifyImages=request.getSession().getServletContext().getRealPath("/verifyImages");
            File file = new File(verifyImages+File.separator);
            if(!file.exists()){
                file.mkdir();
            }
            String fileName=System.currentTimeMillis()+".jpg";
            if(sessionWebClient!=null && sessionLoginPage!=null){
                final WebClient webClient = (WebClient)sessionWebClient;
                UnexpectedPage verifyCodeImagePage = webClient.getPage(verifyCodeImageUrl);
                BufferedImage bi= ImageIO.read(verifyCodeImagePage.getInputStream());
                ImageIO.write(bi, "JPG", new File(verifyImages,fileName));
            }else{
                final WebClient webClient = new WebClient(BrowserVersion.CHROME);
                webClient.getOptions().setCssEnabled(false);// 禁用css支持
                webClient.getOptions().setThrowExceptionOnScriptError(false);// 忽略js异常
                webClient.getOptions().setTimeout(8000); // 设置连接超时时间
                final HtmlPage loginPage = webClient.getPage(loginUrl);
                HtmlImage verifyCodeImagePage = (HtmlImage)loginPage.getByXPath("//img").get(20);
                BufferedImage bi=verifyCodeImagePage.getImageReader().read(0);
                ImageIO.write(bi, "JPG", new File(verifyImages,fileName));
                session.setAttribute("sessionWebClient", webClient);
                session.setAttribute("sessionLoginPage", loginPage);
            }
            data.put("imageUrl",request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+"/verifyImages/"+fileName);
            data.put("ResultInfo","查询成功");
            data.put("ResultCode","0000");
        } catch (IOException e) {
            data.put("ResultInfo","服务器繁忙，请稍后再试！");
            data.put("ResultCode","0002");
        }
        map.put("data",data);
        return map;
    }


}