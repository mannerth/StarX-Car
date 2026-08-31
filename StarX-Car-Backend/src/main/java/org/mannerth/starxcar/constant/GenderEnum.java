package org.mannerth.starxcar.constant;

import lombok.Getter;






@Getter
public enum GenderEnum {
    male("male"),
    female("female");

    private final String name;
    GenderEnum(String name){
        this.name=name;
    }
}