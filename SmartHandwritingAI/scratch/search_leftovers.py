import os

search_terms = ['switch_main_math_mode', 'card_main_modes', 'isMathMode', 'switchMathMode']
project_dir = r'd:\ki2nam3\MoBai\android-handwriting-number-recognition\SmartHandwritingAI'

for root, dirs, files in os.walk(os.path.join(project_dir, 'app', 'src')):
    for f in files:
        if f.endswith(('.java', '.xml')):
            filepath = os.path.join(root, f)
            try:
                with open(filepath, 'r', encoding='utf-8') as file:
                    for idx, line in enumerate(file, 1):
                        for term in search_terms:
                            if term in line:
                                # Skip DrawActivity self-references since it retains its math mode
                                if 'DrawActivity.java' in filepath and term in ['isMathMode', 'switchMathMode']:
                                    continue
                                # Skip FractionParser self-references for isMathMode parameter
                                if 'FractionParser.java' in filepath and term == 'isMathMode':
                                    continue
                                print(f"Found '{term}' in {os.path.relpath(filepath, project_dir)}:{idx}: {line.strip()}")
            except Exception as e:
                pass
print("Search complete.")
