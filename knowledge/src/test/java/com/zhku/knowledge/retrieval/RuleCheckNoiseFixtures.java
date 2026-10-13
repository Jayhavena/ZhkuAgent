package com.zhku.knowledge.retrieval;
import java.util.List;
import java.util.ArrayList;

/** 只在临时库使用的合成近词干扰；不包含真实参考答案或新的校园政策。 */
final class RuleCheckNoiseFixtures {
    static List<String> texts(int count){
        if(count!=0&&count!=40&&count!=100)throw new IllegalArgumentException("合成压力规模只允许0/40/100");
        var families=List.of(
            "奖学金申报表填写学生专业、公共选修课和网络课程学分、考试成绩；材料统计不能判断选课资格。",
            "图书馆每学期安排公共选修主题阅读讲座，报名人数与学时用于活动统计，不能判断课程开班。",
            "志愿服务登记包含本科学生培养方案、必修与专业选修学习时间，不提供课程重修或缓考办理规则。",
            "医保咨询收集学生网络报名、选课时间和申请记录，网上入口与课程成绩仅作联系信息，不提供公选规定。",
            "餐厅满意度调查收集公共选修、专业学习安排与成绩合格情况，不是每学期选修学分上限依据。");
        var values=new ArrayList<String>();
        // 固定近词压力材料独立于题号/答案构造；“合成”标签仅区分证据来源，不参与过滤排名。
        for(int i=0;i<count;i++)values.add("合成压力样本"+(i+1)+"，不是校园政策。"+families.get(i%families.size()));
        return List.copyOf(values);
    }
}
