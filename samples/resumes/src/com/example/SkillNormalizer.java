package com.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SkillNormalizer {

    private static final Map<String, String> NORMALIZATION_MAP = new HashMap<>();

    static {
        NORMALIZATION_MAP.put("docke", "docker");
        NORMALIZATION_MAP.put("liqu", "liquibase");
        NORMALIZATION_MAP.put("liqubase", "liquibase");
        NORMALIZATION_MAP.put("k8s", "kubernetes");
        NORMALIZATION_MAP.put("kubernates", "kubernetes");
        NORMALIZATION_MAP.put("kuber", "kubernetes");
        NORMALIZATION_MAP.put("solyd", "solid");
        NORMALIZATION_MAP.put("solid principles", "solid");
        NORMALIZATION_MAP.put("solid design principles", "solid");
        NORMALIZATION_MAP.put("hibernate", "hibernate orm");
        NORMALIZATION_MAP.put("spring", "spring framework");
        NORMALIZATION_MAP.put("maven", "apache maven");
        NORMALIZATION_MAP.put("js", "javascript");
        NORMALIZATION_MAP.put("reactjs", "react");
        NORMALIZATION_MAP.put("restful api", "rest api");
        NORMALIZATION_MAP.put("rest api", "rest api");
        NORMALIZATION_MAP.put("junit5", "junit");
        NORMALIZATION_MAP.put("kafka", "apache kafka");
        NORMALIZATION_MAP.put("apache kafka", "apache kafka");
        NORMALIZATION_MAP.put("oracle db", "oracle");
        NORMALIZATION_MAP.put("ms sql", "ms sql server");
        NORMALIZATION_MAP.put("ms sql server", "ms sql server");
        NORMALIZATION_MAP.put("psql", "postgresql");
        NORMALIZATION_MAP.put("oop", "ООП");
        NORMALIZATION_MAP.put("docker compose", "docker-compose");
        NORMALIZATION_MAP.put("docker-compose", "docker-compose");
        NORMALIZATION_MAP.put("gitlab", "gitlab");
        NORMALIZATION_MAP.put("github", "github");
        NORMALIZATION_MAP.put("bitbucket", "bitbucket");
        NORMALIZATION_MAP.put("jira", "atlassian jira");
        NORMALIZATION_MAP.put("confluence", "atlassian confluence");
        NORMALIZATION_MAP.put("teamcity", "teamcity");
        NORMALIZATION_MAP.put("jenkins", "jenkins");
        NORMALIZATION_MAP.put("gitlab ci", "gitlab ci");
        NORMALIZATION_MAP.put("ci/cd gitlab", "gitlab ci");
        NORMALIZATION_MAP.put("golang", "go");
        NORMALIZATION_MAP.put("algorithms", "алгоритмы и структуры данных");
        NORMALIZATION_MAP.put("data structures", "алгоритмы и структуры данных");
        NORMALIZATION_MAP.put("английский", "английский язык");
        NORMALIZATION_MAP.put("english", "английский язык");
        NORMALIZATION_MAP.put("level not specified", "уровень не указан");
        NORMALIZATION_MAP.put("medium level", "средний уровень");
        NORMALIZATION_MAP.put("basic level", "базовый уровень");
    }


    public static void main(String[] args) throws IOException {
        System.out.println(new Object().equals(null));
        List<String> normalisedSkills = processFile("skills.txt");
        writeOutput("skills_normalised.txt", normalisedSkills);
    }

    private static void writeOutput(String outputFile, List<String> skills) throws IOException {
        Files.write(Paths.get(outputFile), skills, StandardCharsets.UTF_8);
    }

    private static List<String> processFile(String inputFile) throws IOException {
        Map<String, Integer> map = Files.lines(Paths.get(inputFile), StandardCharsets.UTF_8)
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(line -> !line.isEmpty())
                .map(line -> NORMALIZATION_MAP.getOrDefault(line, line))
                .collect(Collectors.groupingBy(
                        Function.identity(),
                        Collectors.summingInt(word -> 1)
                ));

        return map.entrySet()
                .stream()
                .sorted(Comparator.<Map.Entry<String, Integer>, Integer>comparing(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> String.format("%-30s = %d", entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

}
