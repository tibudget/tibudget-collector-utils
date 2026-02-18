package com.tibudget.utils;

public class IdentityDto {

    private String civility;
    private String firstName;
    private String lastName;
    private String company;
    private String birthDate;
    private String birthCountry;
    private String birthDepartment;
    private String birthCity;
    private boolean isIDChecked;

    public String getCivility() {
        return civility;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getCompany() {
        return company;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getBirthCountry() {
        return birthCountry;
    }

    public String getBirthDepartment() {
        return birthDepartment;
    }

    public String getBirthCity() {
        return birthCity;
    }

    public boolean isIDChecked() {
        return isIDChecked;
    }

    @Override
    public String toString() {
        return "IdentityDto{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}
