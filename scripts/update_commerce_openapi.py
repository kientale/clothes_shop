"""Refresh commerce, reviews and marketing OpenAPI from their Java records and controllers.

Run from any directory: python scripts/update_commerce_openapi.py
Other existing API definitions are preserved.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/lemonadex/project/clothes/features"
PREFIX = "Commerce"
spec_path = ROOT / "docs/openapi.json"
spec = json.loads(spec_path.read_text(encoding="utf-8"))
schemas = spec["components"]["schemas"]
enums = {}
records = {}
report_records = set()


def closing(source, start):
    depth = 0
    quoted = False
    escaped = False
    for index in range(start, len(source)):
        char = source[index]
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("Unbalanced Java declaration")


def fields(source):
    parts = []
    start = depth = 0
    quoted = escaped = False
    for index, char in enumerate(source):
        if quoted:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                quoted = False
        elif char == '"':
            quoted = True
        elif char in "(<":
            depth += 1
        elif char in ")>":
            depth -= 1
        elif char == "," and depth == 0:
            parts.append(source[start:index].strip())
            start = index + 1
    tail = source[start:].strip()
    if tail:
        parts.append(tail)
    return parts


def declaration(field):
    # Annotation arguments can contain regex parentheses inside quoted strings.
    chunks, cursor = [], 0
    while match := re.search(r"@\w+", field[cursor:]):
        start, end = cursor + match.start(), cursor + match.end()
        chunks.append(field[cursor:start])
        if end < len(field) and field[end] == "(":
            end = closing(field, end) + 1
        cursor = end
    chunks.append(field[cursor:])
    clean = "".join(chunks).strip()
    java_type, name = clean.rsplit(None, 1)
    return re.sub(r"\s+", "", java_type), name


def ref(name):
    return {"$ref": "#/components/schemas/" + PREFIX + name}


def typed(java_type):
    if java_type.startswith(("List<", "Set<")):
        item = java_type[java_type.index("<") + 1:-1]
        return {"type": "array", "items": typed(item)}
    if java_type.startswith("PageResponse<"):
        item = java_type[len("PageResponse<"):-1]
        name = item + "Page"
        schemas[PREFIX + name] = {
            "type": "object", "required": ["content", "page", "size", "totalElements", "totalPages"],
            "properties": {"content": {"type": "array", "items": typed(item)},
                           **{key: {"type": "integer", "minimum": 0} for key in ("page", "size", "totalElements", "totalPages")}},
        }
        return ref(name)
    if java_type in enums or java_type in records:
        return ref(java_type)
    types = {
        "String": {"type": "string"}, "UUID": {"type": "string", "format": "uuid"},
        "Instant": {"type": "string", "format": "date-time"},
        "LocalDate": {"type": "string", "format": "date"},
        "BigDecimal": {"type": "number", "minimum": 0, "exclusiveMaximum": 10000000000000000, "multipleOf": 0.01},
        "int": {"type": "integer"}, "Integer": {"type": "integer"}, "long": {"type": "integer"}, "Long": {"type": "integer"},
        "Boolean": {"type": "boolean"}, "boolean": {"type": "boolean"}, "Void": {"type": "null"},
    }
    if java_type not in types:
        raise ValueError("Unmapped type: " + java_type)
    return dict(types[java_type])


def constraints(schema, field):
    size = re.search(r"@Size\(([^)]+)\)", field)
    if size:
        for boundary in ("min", "max"):
            value = re.search(boundary + r"\s*=\s*(\d+)", size[1])
            if value:
                suffix = "Items" if schema.get("type") == "array" else "Length"
                schema[boundary + suffix] = int(value[1])
    for annotation, boundary in (("Min", "minimum"), ("Max", "maximum")):
        value = re.search("@" + annotation + r"\((\d+)\)", field)
        if value:
            schema[boundary] = int(value[1])
    if "@NotBlank" in field and schema.get("type") == "string":
        schema["minLength"] = 1
    if "@NotEmpty" in field and schema.get("type") == "array":
        schema["minItems"] = 1
    pattern = re.search(r'@Pattern\(regexp\s*=\s*("(?:[^"\\]|\\.)*")\)', field)
    if pattern:
        value = json.loads(pattern[1])
        if schema.get("type") == "array":
            schema["items"]["pattern"] = value
            schema["items"]["maxLength"] = 2048
        else:
            schema["pattern"] = value
    decimal_min = re.search(r'@DecimalMin\((?:value\s*=\s*)?"([0-9.]+)"', field)
    if decimal_min:
        schema["minimum"] = float(decimal_min[1])
    if "@Email" in field:
        schema["format"] = "email"
    if "inclusive = false" in field:
        schema.pop("minimum", None)
        schema["exclusiveMinimum"] = 0
    return schema


for feature in ("inventory", "order", "marketing", "review", "content", "report", "settings"):
    for path in (JAVA / feature / "model").glob("*.java"):
        match = re.search(r"public enum (\w+)\s*\{([^}]+)\}", path.read_text(encoding="utf-8"))
        if match:
            enums[match[1]] = [value.strip() for value in match[2].split(",")]
    for path in (JAVA / feature / "dto").glob("*.java"):
        source = path.read_text(encoding="utf-8")
        for match in re.finditer(r"public record (\w+)\(", source):
            start = match.end() - 1
            records[match[1]] = (fields(source[start + 1:closing(source, start)]), path.name.endswith("Requests.java"))
            if feature == "report":
                report_records.add(match[1])

for name, values in enums.items():
    schemas[PREFIX + name] = {"type": "string", "enum": values}
for name, (members, request) in records.items():
    properties, required = {}, []
    for member in members:
        java_type, field = declaration(member)
        schema = constraints(typed(java_type), member) if request else typed(java_type)
        mandatory = not request or any(annotation in member for annotation in ("@NotNull", "@NotBlank", "@NotEmpty"))
        nullable = (request and not mandatory) or (not request and (
            field in ("note", "referenceType", "referenceId", "createdBy", "changedBy", "processedBy", "trackingCode", "providerTransactionId",
                      "description", "conditionNote", "reason", "inventoryId", "warehouseId", "replacementVariantId", "replacementInventoryId",
                      "maxDiscount", "usageLimit", "linkUrl", "comment", "moderationNote", "moderatedBy", "flashSaleItemId", "promotionId", "couponId", "thumbnailUrl", "updatedBy", "shippingMethodCode", "methodCode", "provider", "estimatedDays", "oldData", "newData", "accountId")
            or name == "CampaignRow" and field == "code"
            or java_type == "Instant" and field not in ("createdAt", "updatedAt", "requestedAt", "placedAt")))
        if nullable:
            if "type" in schema:
                schema["type"] = [schema["type"], "null"]
            else:
                schema = {"anyOf": [schema, {"type": "null"}]}
        if not request and java_type == "Instant" and (field == "usedAt" or field in ("startAt", "endAt") and name in ("CouponResponse", "PromotionResponse", "FlashSaleResponse")):
            schema = {"type": "string", "format": "date-time"}
        if name in report_records and java_type == "BigDecimal":
            schema = {"type": "number", "multipleOf": 0.01}
        if not request and java_type == "Instant" and (name in report_records and field in ("from", "to", "asOf") or name in ("ArticleSummaryResponse", "PublishedArticleResponse", "ActivePolicyResponse") and field in ("publishedAt", "activatedAt")):
            schema = {"type": "string", "format": "date-time"}
        properties[field] = schema
        if mandatory:
            required.append(field)
    schemas[PREFIX + name] = {"type": "object", "properties": properties, "required": required, "additionalProperties": False}

operations = 0
schemas[PREFIX + "MovementRequest"]["properties"]["transactionType"] = {"type": "string", "enum": ["RECEIPT", "ISSUE"]}
for feature in ("inventory", "order", "marketing", "review", "content", "report", "settings"):
    for path in (JAVA / feature / "controller").glob("*.java"):
        source = path.read_text(encoding="utf-8")
        base_path = re.search(r'@RequestMapping\("([^"]+)"\)', source)[1]
        mappings = list(re.finditer(r'@(Get|Post|Put|Delete)Mapping\("([^"]+)"\)', source))
        for index, match in enumerate(mappings):
            block = source[match.end():mappings[index + 1].start() if index + 1 < len(mappings) else len(source)]
            method = re.search(r"ResponseEntity<ApiResponse<(.+)>>\s+(\w+)\(", block)
            if not method:
                raise ValueError("Unparsed controller method: " + path.name + " " + match[2])
            java_type, method_name = method[1], method[2]
            args_start = method.end() - 1
            arguments = fields(block[args_start + 1:closing(block, args_start)])
            parameters, body = [], None
            for argument in arguments:
                argument_type, name = declaration(argument)
                if "@RequestBody" in argument:
                    body = {"required": True, "content": {"application/json": {"schema": typed(argument_type)}}}
                elif "@PathVariable" in argument or "@RequestParam" in argument:
                    schema = constraints(typed(argument_type), argument)
                    default = re.search(r'defaultValue\s*=\s*"([^"]+)"', argument)
                    if default:
                        schema["default"] = (int(default[1]) if argument_type == "int" else
                                             default[1] == "true" if argument_type in ("boolean", "Boolean") else default[1])
                    where = "path" if "@PathVariable" in argument else "query"
                    parameter = {"name": name, "in": where, "required": where == "path" or ("@RequestParam" in argument and "required = false" not in argument and not default), "schema": schema}
                    if name == "from":
                        parameter["description"] = "Inclusive UTC/ISO-8601 lower creation-time bound."
                    if name == "to":
                        parameter["description"] = "Exclusive UTC/ISO-8601 upper creation-time bound; must be after from."
                    if feature == "report" and name in ("from", "to"):
                        event = "payment/refund event" if match[2].endswith("/revenue") else "return request" if match[2].endswith("/returns") else "order placement"
                        parameter["description"] = ("Inclusive lower " if name == "from" else "Exclusive upper ") + event + " time bound. Period must be positive and at most 366 days."
                    parameters.append(parameter)
            permission_match = re.search(r"hasAuthority\('([^']+)'\)", block)
            permission = permission_match[1] if permission_match else None
            data = typed(java_type)
            envelope_name = path.stem + method_name[0].upper() + method_name[1:] + "Envelope"
            schemas[PREFIX + envelope_name] = {"allOf": [{"$ref": "#/components/schemas/ApiResponse"},
                {"type": "object", "properties": {"success": {"const": True}, "data": data}, "required": ["success", "data"]}]}
            code = "201" if "return created(" in block else "200"
            public_content = base_path in ("/api/v1/content", "/api/v1/store")
            description = "Published content; no authentication required." if public_content else ("Requires ADMIN role and " + permission + "." if permission else "Requires CUSTOMER role and an active linked customer profile.")
            operation = {"tags": [{"inventory": "Inventory", "order": "Orders", "marketing": "Marketing", "review": "Reviews", "content": "Content", "report": "Reports", "settings": "Settings"}[feature]],
                "operationId": "commerce" + path.stem + method_name[0].upper() + method_name[1:],
                "summary": re.sub(r"(?<!^)([A-Z])", r" \1", method_name).capitalize(),
                "description": description + " See docs/system-settings.md, docs/content-reports.md, docs/customer-marketing.md and docs/inventory-orders.md for business rules.",
                "security": [] if public_content else [{"bearerAuth": []}], "parameters": parameters,
                "responses": {code: {"description": "Success", "content": {"application/json": {"schema": ref(envelope_name)}}},
                    **{error: {"description": description, "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiResponse"}}}}
                        for error, description in (("400", "Missing or invalid values."), ("401", "Authentication required."),
                                                   ("403", "Missing role or permission."), ("404", "Resource not found."),
                                                   ("409", "Conflicting state, stock, payment or refund limit."))}}}
            if body:
                operation["requestBody"] = body
            spec["paths"].setdefault(base_path + match[2], {})[match[1].lower()] = operation
            operations += 1

spec_path.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Updated {operations} operations and {len(records)} DTO records.")
