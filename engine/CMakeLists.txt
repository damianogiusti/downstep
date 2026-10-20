cmake_minimum_required(VERSION 3.30)
# Without it the library targets the build machine's macOS and does not load on older systems.
# 11.0 is the floor of the bundled Java runtime and Skiko, so the engine is never the limit.
# FORCE only when empty: older build dirs cached an empty value, -D still wins.
if(NOT CMAKE_OSX_DEPLOYMENT_TARGET)
  set(CMAKE_OSX_DEPLOYMENT_TARGET "11.0" CACHE STRING "Minimum macOS version" FORCE)
endif()
project(slowdowner_engine CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Static third-party code ends up inside a shared library.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(NOT CMAKE_BUILD_TYPE)
  # Bungee is several times slower without optimisation.
  set(CMAKE_BUILD_TYPE Release)
endif()
# Every binary straight in the build dir, also with Visual Studio's per-config generators
# (the empty generator expression stops the Release/ subfolder): Gradle looks for them there.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}$<0:>)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}$<0:>)

if(MSVC)
  # Bungee uses GCC extensions (__attribute__, unistd.h) and is released for Windows with MinGW.
  message(FATAL_ERROR "Build with GCC or Clang. On Windows use MinGW-w64: see .github/workflows/windows.yml")
endif()
if(MINGW)
  # No MinGW runtime DLLs to ship next to the engine (same flags as Bungee's own Windows build).
  set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -static-libgcc -static-libstdc++ -static")
  set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -static-libgcc -static-libstdc++ -static")
endif()

include(FetchContent)

set(BUNGEE_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
FetchContent_Declare(bungee
  GIT_REPOSITORY https://github.com/bungee-audio-stretch/bungee.git
  GIT_TAG v2.4.8
  GIT_SHALLOW ON
  GIT_SUBMODULES submodules/eigen submodules/pffft submodules/cxxopts
  EXCLUDE_FROM_ALL)

# Only the sources: miniaudio is compiled below as a single translation unit.
FetchContent_Declare(miniaudio
  GIT_REPOSITORY https://github.com/mackron/miniaudio.git
  GIT_TAG 0.11.25
  GIT_SHALLOW ON
  SOURCE_SUBDIR do-not-build)

# LAME has no CMake build: only the sources, compiled below. LGPL: on Windows, where the app is
# shared with others, it is a separate DLL that can be replaced. On macOS it is linked statically.
FetchContent_Declare(lame
  URL https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz
  URL_HASH SHA256=ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e
  DOWNLOAD_EXTRACT_TIMESTAMP ON
  SOURCE_SUBDIR do-not-build)

FetchContent_MakeAvailable(bungee miniaudio lame)

set(LAME_DIR ${lame_SOURCE_DIR}/libmp3lame)
if(WIN32)
  set(LAME_LIBRARY_TYPE SHARED)
else()
  set(LAME_LIBRARY_TYPE STATIC)
endif()
add_library(mp3lame ${LAME_LIBRARY_TYPE}
  ${LAME_DIR}/VbrTag.c ${LAME_DIR}/bitstream.c ${LAME_DIR}/encoder.c ${LAME_DIR}/fft.c
  ${LAME_DIR}/gain_analysis.c ${LAME_DIR}/id3tag.c ${LAME_DIR}/lame.c ${LAME_DIR}/mpglib_interface.c
  ${LAME_DIR}/newmdct.c ${LAME_DIR}/presets.c ${LAME_DIR}/psymodel.c ${LAME_DIR}/quantize.c
  ${LAME_DIR}/quantize_pvt.c ${LAME_DIR}/reservoir.c ${LAME_DIR}/set_get.c ${LAME_DIR}/tables.c
  ${LAME_DIR}/takehiro.c ${LAME_DIR}/util.c ${LAME_DIR}/vbrquantize.c ${LAME_DIR}/version.c)
target_include_directories(mp3lame PRIVATE third_party/lame ${LAME_DIR} ${lame_SOURCE_DIR}/include)
target_include_directories(mp3lame INTERFACE ${lame_SOURCE_DIR}/include)
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
target_compile_options(mp3lame PRIVATE -w)

add_library(miniaudio STATIC ${miniaudio_SOURCE_DIR}/miniaudio.c)
target_include_directories(miniaudio PUBLIC ${miniaudio_SOURCE_DIR})
target_compile_definitions(miniaudio PUBLIC MA_NO_DECODING MA_NO_ENCODING MA_NO_GENERATION)

if(APPLE)
  set(DECODER_SOURCE src/DecoderApple.cpp)
  set(DECODER_LIBRARIES "-framework AudioToolbox" "-framework CoreFoundation" "-framework CoreAudio")
elseif(WIN32)
  set(DECODER_SOURCE src/DecoderWindows.cpp)
  set(DECODER_LIBRARIES mfplat mfreadwrite mfuuid ole32)
else()
  message(FATAL_ERROR "No audio decoder for ${CMAKE_SYSTEM_NAME} yet")
endif()

set(ENGINE_SOURCES
  src/CApi.cpp
  ${DECODER_SOURCE}
  src/Loudness.cpp
  src/Mp3Export.cpp
  src/Player.cpp
  src/TimeStretcher.cpp
  src/Wav.cpp)

function(configure_engine_target target)
  target_include_directories(${target} PRIVATE include src ${bungee_SOURCE_DIR})
  target_link_libraries(${target} PRIVATE bungee_library miniaudio mp3lame ${DECODER_LIBRARIES})
  target_compile_options(${target} PRIVATE -Wall -Wextra)
endfunction()

# The library loaded by the JVM (FFM). Only the sd_engine_* and sd_export_* functions are exported.
add_library(sdengine SHARED ${ENGINE_SOURCES})
configure_engine_target(sdengine)
set_target_properties(sdengine PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
if(WIN32)
  # sdengine.dll and mp3lame.dll, not MinGW's libsdengine.dll: the names the app looks for.
  set_target_properties(sdengine mp3lame PROPERTIES PREFIX "")
endif()
if(APPLE)
  # Bungee and miniaudio are built with default visibility: keep their symbols out of the export table.
  target_link_options(sdengine PRIVATE "LINKER:-exported_symbol,_sd_engine_*" "LINKER:-exported_symbol,_sd_export_*")
endif()

# Tests use the C API in manual mode plus a few internals, so they compile the sources directly.
add_executable(sdengine_tests tests/EngineTests.cpp ${ENGINE_SOURCES})
configure_engine_target(sdengine_tests)

enable_testing()
add_test(NAME sdengine_tests COMMAND sdengine_tests)
