package com.tuoguan.backend.course.web;

// 双重身份学生（同时有托管班）的用餐走托管看板那套，不在课外消课页面重复记录——
// 避免同一天的用餐被两条链路各记一次、各算一次钱。
public class OffCampusMealNotApplicableException extends RuntimeException {

    public OffCampusMealNotApplicableException(String message) {
        super(message);
    }
}
