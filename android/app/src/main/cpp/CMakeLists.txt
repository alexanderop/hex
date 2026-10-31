cmake_minimum_required(VERSION 3.22.1)
project(hexwhisper)

set(WHISPER_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp")
if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp is missing at ${WHISPER_DIR}. Run the downloadWhisper Gradle task.")
endif()

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

# Debug APKs still need a usable CPU fallback. -O0 makes tiny unusable on a phone.
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")

add_subdirectory("${WHISPER_DIR}" "${CMAKE_BINARY_DIR}/whisper" EXCLUDE_FROM_ALL)

add_library(hexwhisper SHARED whisper_jni.cpp)
target_compile_definitions(hexwhisper PRIVATE HEX_HAS_WHISPER=1)
target_compile_features(hexwhisper PRIVATE cxx_std_17)
target_link_libraries(hexwhisper PRIVATE whisper log)
target_link_options(hexwhisper PRIVATE "-Wl,-z,max-page-size=16384")
