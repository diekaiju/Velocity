cmake_minimum_required(VERSION 3.22.1)

project("browser")

add_library(
        browser
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        browser
        ${log-lib})
