cmake_minimum_required(VERSION 3.18.0)
project(setchip-tb)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

include_directories(${CMAKE_CURRENT_BINARY_DIR})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

add_compile_options(-Wall -Wextra -std=c++20)

find_package(verilator
  HINTS $ENV{VERILATOR_ROOT}
)

add_executable(
  setchip-exec testbench-soc.cpp
)

verilate(
  setchip-exec
  SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/../SetChip.v
  PREFIX VSetChip
  TRACE # TODO: change to TRACE_FST
  DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
  VERILATOR_ARGS -Wno-WIDTH --trace-params --trace-structs --top-module SetChip
)