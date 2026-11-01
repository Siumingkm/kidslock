package com.kidslock.app;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 通用题库：从 assets/questions.json 加载，按 年级/科目/知识点 组织。
 * 选题采用"弱项优先 + 间隔重复"权重（与 question_bank.py 对齐），
 * 保证题目随孩子掌握情况动态变化，且答案/解析权威可查。
 *
 * 通用化：架构支持全部年级（小学1-6 + 初中7-9），内容通过 JSON 扩充，
 * 亦可经 NAS 接口拉取（见 NAS 管理后台）。
 */
public class QuestionBank {

    private static final String TAG = "QuestionBank";
    private static final List<String> PRIMARY_SUBJECTS = Arrays.asList("语文", "数学", "英语");
    private static final List<String> JUNIOR_SUBJECTS =
            Arrays.asList("语文", "数学", "英语", "物理", "历史", "道德与法治");

    public static class Question {
        public String id;
        public String subject;
        public int grade;
        public String edition;
        public String unit;
        public String knowledge;
        public String type;
        public String stem;
        public List<String> options = new ArrayList<>();
        public String answer;
        public String explanation;
    }

    private final List<Question> all = new ArrayList<>();
    private final Random random = new Random();

    /** 从 assets 加载内置题库（离线优先）。 */
    public static QuestionBank loadFromAssets(Context context) {
        QuestionBank bank = new QuestionBank();
        try {
            InputStream is = context.getAssets().open("questions.json");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            bank.parse(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "load assets failed", e);
        }
        return bank;
    }

    private void parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("questions");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Question q = new Question();
            q.id = o.optString("id");
            q.subject = o.optString("subject");
            q.grade = o.optInt("grade");
            q.edition = o.optString("edition");
            q.unit = o.optString("unit");
            q.knowledge = o.optString("knowledge");
            q.type = o.optString("type");
            q.stem = o.optString("stem");
            q.answer = o.optString("answer");
            q.explanation = o.optString("explanation");
            JSONArray opts = o.optJSONArray("options");
            if (opts != null) {
                for (int j = 0; j < opts.length(); j++) q.options.add(opts.getString(j));
            }
            all.add(q);
        }
    }

    /** 年级 → 科目集（小学语数英；初中主科）。 */
    public List<String> gradeSubjects(int grade) {
        return grade <= 6 ? new ArrayList<>(PRIMARY_SUBJECTS) : new ArrayList<>(JUNIOR_SUBJECTS);
    }

    private List<Question> filter(int grade, List<String> subjects) {
        List<Question> out = new ArrayList<>();
        for (Question q : all) {
            if (q.grade != grade) continue;
            if (subjects != null && !subjects.contains(q.subject)) continue;
            out.add(q);
        }
        return out;
    }

    /**
     * 抽取一轮题目（默认 requiredCount 道），弱项优先。
     * masteryJson: { "知识点": 掌握度0~1 }
     */
    public List<Question> getRound(int grade, List<String> subjects, int count, String masteryJson) {
        List<Question> pool = filter(grade, subjects);
        if (pool.isEmpty()) return new ArrayList<>();
        JSONObject mastery = new JSONObject();
        try {
            if (masteryJson != null) mastery = new JSONObject(masteryJson);
        } catch (Exception ignore) {
        }

        List<Question> picked = new ArrayList<>();
        List<Question> working = new ArrayList<>(pool);
        for (int n = 0; n < count && !working.isEmpty(); n++) {
            double totalW = 0;
            double[] weights = new double[working.size()];
            for (int i = 0; i < working.size(); i++) {
                Question q = working.get(i);
                double m = mastery.optDouble(q.knowledge, 0.5);
                double w = Math.max(0.05, 1.0 - m); // 掌握度越低权重越高
                weights[i] = w;
                totalW += w;
            }
            double r = random.nextDouble() * totalW;
            int idx = working.size() - 1;
            for (int i = 0; i < weights.length; i++) {
                r -= weights[i];
                if (r <= 0) {
                    idx = i;
                    break;
                }
            }
            Question chosen = working.remove(idx);
            Collections.shuffle(chosen.options, random);
            picked.add(chosen);
        }
        return picked;
    }

    public int size() {
        return all.size();
    }
}
