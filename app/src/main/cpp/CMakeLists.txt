cmake_minimum_required(VERSION 3.22)
project(callnote)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Mã nguồn whisper.cpp được tải về thư mục này khi build (xem workflow)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(callnote SHARED jni.cpp)
target_include_directories(callnote PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/ggml/include)
target_compile_options(callnote PRIVATE -O3)
find_library(LOG_LIB log)
target_link_libraries(callnote whisper ${LOG_LIB} android)
