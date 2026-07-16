import os
import shutil

# Root path
root = "c:\\Users\\SSAFY\\Desktop\\dev\\lets_meet_on_friday"

# Source and destination mappings
moves = [
    # 1. core
    ("app/src/main/java/com/kosmos/app/core", "core/src/main/java/com/kosmos/app/core"),
    # 2. domain
    ("app/src/main/java/com/kosmos/app/domain", "domain/src/main/java/com/kosmos/app/domain"),
    # 3. data
    ("app/src/main/java/com/kosmos/app/data", "data/src/main/java/com/kosmos/app/data"),
]

# Perform migrations
for src_rel, dest_rel in moves:
    src_path = os.path.join(root, src_rel.replace("/", os.sep))
    dest_path = os.path.join(root, dest_rel.replace("/", os.sep))
    
    if os.path.exists(src_path):
        dest_parent = os.path.dirname(dest_path)
        if not os.path.exists(dest_parent):
            os.makedirs(dest_parent)
        print(f"Moving {src_path} -> {dest_path}")
        shutil.move(src_path, dest_path)
    else:
        print(f"Source not found: {src_path}")

# 4. Relocate AuditTrailService.kt to domain
audit_src = os.path.join(root, "app/src/main/java/com/kosmos/app/assistant/audit/AuditTrailService.kt".replace("/", os.sep))
audit_dest_dir = os.path.join(root, "domain/src/main/java/com/kosmos/app/domain/audit".replace("/", os.sep))
audit_dest = os.path.join(audit_dest_dir, "AuditTrailService.kt")

if os.path.exists(audit_src):
    if not os.path.exists(audit_dest_dir):
        os.makedirs(audit_dest_dir)
    print(f"Moving AuditTrailService {audit_src} -> {audit_dest}")
    shutil.move(audit_src, audit_dest)
else:
    print(f"AuditTrailService not found at {audit_src}")

# 5. Move Hilt DI modules DatabaseModule.kt, DataStoreModule.kt, MemoryModule.kt to data module
di_files = ["DatabaseModule.kt", "DataStoreModule.kt", "MemoryModule.kt"]
di_dest_dir = os.path.join(root, "data/src/main/java/com/kosmos/app/data/di".replace("/", os.sep))

if not os.path.exists(di_dest_dir):
    os.makedirs(di_dest_dir)

for di_file in di_files:
    di_src = os.path.join(root, f"app/src/main/java/com/kosmos/app/di/{di_file}".replace("/", os.sep))
    di_dest = os.path.join(di_dest_dir, di_file)
    if os.path.exists(di_src):
        print(f"Moving DI module {di_src} -> {di_dest}")
        shutil.move(di_src, di_dest)
    else:
        print(f"DI module not found at {di_src}")
