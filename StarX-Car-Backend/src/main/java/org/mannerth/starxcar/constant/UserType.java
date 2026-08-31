package org.mannerth.starxcar.constant;

import lombok.Getter;






@Getter
public enum UserType {
    admin("admin"),
    passenger("passenger"),
    driver("driver");

    private final String name;

    UserType(String name) {
        this.name = name;
    }
}