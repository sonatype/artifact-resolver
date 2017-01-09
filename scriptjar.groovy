#!/usr/bin/env groovy

/*
MIT License

Copyright (c) 2016 Dmitrijs Artjomenko

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

// modified version of https://github.com/dmitart/scriptjar

import groovy.grape.Grape
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.tools.GroovyClass

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

import groovy.io.FileType

import static org.codehaus.groovy.control.Phases.CLASS_GENERATION

List<GroovyClass> compile(String prefix, File file) {
    List<GroovyClass> classes = [] as List<GroovyClass>

    // set up classloader with all the grapes loaded
    final GroovyClassLoader classLoader = new GroovyClassLoader()
    classLoader.parseClass(file.text)
    getSiblingGroovyFiles(file).each {
      classLoader.parseClass(it.text)
    }
    
    // disable groovy grapes - we're resolving these ahead of time
    CompilerConfiguration compilerConfig = new CompilerConfiguration()
    Set disabledTransforms = ['groovy.grape.GrabAnnotationTransformation'] as Set
    compilerConfig.setDisabledGlobalASTTransformations(disabledTransforms)

    // compile main class
    CompilationUnit unit = new CompilationUnit(compilerConfig, null, classLoader)
    unit.addSource(SourceUnit.create(prefix, file.text))
    println "${file.name} => ${prefix} (main class)"
    unit.compile(CLASS_GENERATION)
    classes += unit.getClasses()

    // compile groovy files in same folder
    getSiblingGroovyFiles(file).each {
        CompilationUnit dependentUnit = new CompilationUnit(compilerConfig, null, classLoader)
        def className = it.name.replaceAll(/\.groovy$/, '')
        println "${it.name} => ${className} (lib)"
        dependentUnit.addSource(SourceUnit.create(className, it.text))
        dependentUnit.compile(CLASS_GENERATION)

        classes += dependentUnit.getClasses()
    }

    return classes
}

List<File> getSiblingGroovyFiles(File mainGroovyFile) {
    def groovyFileRe = /.*\.groovy$/
    List<File> files = [] as List<File>
    mainGroovyFile.getAbsoluteFile().getParentFile().eachFile(FileType.FILES) {
        if (!it.hidden && it.name =~ groovyFileRe && it.name != mainGroovyFile.name && it.name != 'scriptjar.groovy') {
            files << it
        }
    }

    return files
}

List<File> getGroovyLibs(List neededJars) {
    def libs = new File('.')
    if (System.getenv('GROOVY_HOME')) {
        libs = new File(System.getenv('GROOVY_HOME'), 'lib')
    }else if( System.getProperty("user.home") &&
              new File( System.getProperty("user.home"), '.groovy/grapes' ).exists() ) {
        libs = new File( System.getProperty("user.home"), '.groovy/grapes' )
    } else {
        println "Cann't find GROOVY_HOME"
        System.exit(1)
    }
    def groovylibs = libs.listFiles().findAll{jar ->
        neededJars.any{needed -> jar.name =~ needed  }
    }
    if (groovylibs) {
       return groovylibs
    } else {
        println "Can't find Groovy lib in ${libs.absolutePath}, specify it manually as Grab dependency"
        System.exit(1)
    }
}

List dependencies(File source) {
    final GroovyClassLoader classLoader = new GroovyClassLoader()
    classLoader.parseClass(source.text)
    getSiblingGroovyFiles(source).each {
      classLoader.parseClass(it.text)
    }
    def files = Grape.resolve([:], Grape.listDependencies(classLoader)).collect{ new JarFile(it.path) }
    files.addAll(getGroovyLibs([/groovy-\d+.\d+.\d+.jar/]).collect{ new JarFile(it) })

    return files
}

void writeJarEntry(JarOutputStream jos, JarEntry entry, byte[] data) {
    entry.setSize(data.length)
    jos.putNextEntry(entry)
    jos.write(data)
}

byte[] createJar(String prefix, List jars, List<GroovyClass> compiled, File resourcesBase, List<String> resources) {
    ByteArrayOutputStream output = new ByteArrayOutputStream()
    JarOutputStream jos = new JarOutputStream(output)

    jos.putNextEntry(new JarEntry('META-INF/'))
    writeJarEntry(jos, new JarEntry('META-INF/MANIFEST.MF'), "Manifest-Version: 1.0\nMain-Class: ${prefix}\n".getBytes())
    compiled.each {
      writeJarEntry(jos, new JarEntry("${it.name}.class"), it.bytes)
    }

    def directories = ['META-INF/', 'META-INF/MANIFEST.MF']

    jars.each {file ->
        println "Merging ${file.name}"
        file.entries().each { JarEntry entry ->
            if (!directories.contains(entry.name)) {
                writeJarEntry(jos, entry, file.getInputStream(entry).getBytes())
                directories << entry.name
            }
        }
    }
    resources.each { resource ->
        println "Matching pattern ${resource}"
        List<String> matchedResources = new FileNameFinder().getFileNames(resourcesBase.getAbsolutePath(), resource )
        println "Matched resources $matchedResources"

        matchedResources.each { matchedResource ->
            def entryName = matchedResource.replaceFirst(resourcesBase.getAbsolutePath() + '/', '')
            if (!directories.contains(entryName)) {
                println "Merging $matchedResource as jar entry $entryName"
                writeJarEntry(jos, new JarEntry(entryName), new File(matchedResource).newInputStream().getBytes())
                directories << entryName
            }
        }

    }

    jos.close()
    return output.toByteArray()
}

byte[] createUberjar(File file, String prefix, List<String> resources) {
    List<GroovyClass> compiled = compile(prefix, file)
    def jars = dependencies(file)
    return createJar(prefix, jars, compiled, file.getAbsoluteFile().getParentFile(), resources)
}


if (args.size() < 2) {
    println "Usage: ./scriptjar.groovy input.groovy output.jar [additional resources]"
    System.exit(1)
}

File file = new File(args[0])
String prefix = file.name.substring(0, file.name.indexOf('.'))

List<String> resourcePatterns = Arrays.asList(args).subList(2, args.size())
println "additional args $resourcePatterns"
new File(args[1]).withOutputStream {
    it << createUberjar(file, prefix, resourcePatterns)
}
